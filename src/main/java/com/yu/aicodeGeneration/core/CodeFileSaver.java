package com.yu.aicodeGeneration.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.yu.aicodeGeneration.ai.model.HtmlCodeResult;
import com.yu.aicodeGeneration.ai.model.MultiFileCodeResult;
import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 文件保存器
 * 学习提示：该类已废弃，保留用于理解旧流程；新流程优先看 core/saver 下的模板方法保存器。
 */
@Deprecated
public class CodeFileSaver {

    /**
     * 文件保存的根目录
     * 学习提示：旧版保存器把所有生成代码统一写到项目 tmp/code_output 目录下。
     */
    private static final String FILE_SAVE_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 保存 HTML 网页代码
     *
     * @param htmlCodeResult
     * @return
     */
    public static File saveHtmlCodeResult(HtmlCodeResult htmlCodeResult) {
        // 为本次 HTML 生成创建独立目录，避免不同应用生成结果互相覆盖。
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.HTML.getValue());
        // 单文件模式只写 index.html。
        writeToFile(baseDirPath, "index.html", htmlCodeResult.getHtmlCode());
        return new File(baseDirPath);
    }

    /**
     * 保存多文件网页代码
     *
     * @param result
     * @return
     */
    public static File saveMultiFileCodeResult(MultiFileCodeResult result) {
        // 多文件模式同样先创建唯一目录。
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.MULTI_FILE.getValue());
        // 分别写入 HTML、CSS、JS，形成最基础的静态网站结构。
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        writeToFile(baseDirPath, "script.js", result.getJsCode());
        return new File(baseDirPath);
    }

    /**
     * 构建文件的唯一路径：tmp/code_output/bizType_雪花 ID
     *
     * @param bizType 代码生成类型
     * @return
     */
    private static String buildUniqueDir(String bizType) {
        // 使用业务类型 + 雪花 ID 作为目录名，既能看出生成类型，也基本不会冲突。
        String uniqueDirName = StrUtil.format("{}_{}", bizType, IdUtil.getSnowflakeNextIdStr());
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        // mkdir 会递归创建目录。
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 保存单个文件
     *
     * @param dirPath
     * @param filename
     * @param content
     */
    private static void writeToFile(String dirPath, String filename, String content) {
        // 拼接文件完整路径，并按 UTF-8 写入，保证中文和代码内容不乱码。
        String filePath = dirPath + File.separator + filename;
        FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
    }
}
