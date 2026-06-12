package com.yu.aicodeGeneration.core.saver;

// 中文注释：代码保存模块：按照生成类型把解析后的代码落盘到应用输出目录。
// 中文注释：CodeFileSaverTemplate 是该模块中的一个核心类/接口，阅读时可先关注公开方法和被注入的依赖。


/**
 * 阅读提示：
 * 该文件属于：代码保存层：负责创建应用输出目录，并按生成类型把代码结果写入文件系统。
 * 主要关注：重点关注输出目录、文件命名、安全校验和不同生成类型的落盘差异。
 * 阅读顺序建议：先看类上的注解和成员依赖，再看核心 public 方法的调用链。
 */
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.yu.aicodeGeneration.constant.AppConstant;
import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 抽象代码文件保存器 - 模板方法模式
 *
 * @param <T>
 */
public abstract class CodeFileSaverTemplate<T> {

    /**
     * 文件保存的根目录
     */
    private static final String FILE_SAVE_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /*
     * 模板方法模式的核心：
     * 父类规定“校验 -> 建目录 -> 写文件 -> 返回目录”的固定流程，
     * 子类只关心自己这种代码类型应该写哪些文件。
     * 这样可以避免 HTML 保存器、多文件保存器重复写一遍目录创建和参数校验。
     */

    /**
     * 模板方法：保存代码的标准流程
     *
     * @param result 代码结果对象
     * @param appId 应用 ID
     * @return 保存的目录
     */
    public final File saveCode(T result, Long appId) {
        // 1. 验证输入
        validateInput(result);
        // 2. 构建唯一目录
        String baseDirPath = buildUniqueDir(appId);
        // 3. 保存文件（具体实现交给子类）
        saveFiles(result, baseDirPath);
        // 4. 返回文件目录对象
        return new File(baseDirPath);
    }

    /**
     * 写入单个文件的工具方法
     *
     * @param dirPath  目录路径
     * @param filename 文件名
     * @param content  文件内容
     */
    public final void writeToFile(String dirPath, String filename, String content) {
        /*
         * 空内容不写文件，是为了避免 AI 某些字段返回空字符串时生成无意义文件。
         * 这里统一使用 UTF-8，保证生成的 HTML/CSS/JS 能正常包含中文。
         */
        if (StrUtil.isNotBlank(content)) {
            String filePath = dirPath + File.separator + filename;
            FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
        }
    }

    /**
     * 验证输入参数（可由子类覆盖）
     *
     * @param result 代码结果对象
     */
    protected void validateInput(T result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码结果对象不能为空");
        }
    }

    /**
     * 构建文件的唯一路径：tmp/code_output/bizType_雪花 ID
     *
     * @param appId 应用 ID
     * @return 目录路径
     */
    protected String buildUniqueDir(Long appId) {
        if (appId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        }
        /*
         * 目录名包含生成类型和 appId，例如 html_12、multi_file_12。
         * 这样同一个应用在预览、部署、下载时都能根据 appId 反推出代码所在目录。
         */
        String codeType = getCodeType().getValue();
        String uniqueDirName = StrUtil.format("{}_{}", codeType, appId);
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 保存文件（具体实现交给子类）
     *
     * @param result      代码结果对象
     * @param baseDirPath 基础目录路径
     */
    protected abstract void saveFiles(T result, String baseDirPath);

    /**
     * 获取代码生成类型
     *
     * @return 代码生成类型枚举
     */
    protected abstract CodeGenTypeEnum getCodeType();
}
