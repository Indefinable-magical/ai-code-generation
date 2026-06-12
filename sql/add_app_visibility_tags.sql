-- 应用可见范围和标签字段扩展

alter table app
    add column visibility varchar(32) default 'private' not null comment '应用可见范围：public/private' after priority,
    add column tags varchar(1024) null comment '应用标签，英文逗号包裹存储' after visibility,
    add index idx_visibility (visibility);
