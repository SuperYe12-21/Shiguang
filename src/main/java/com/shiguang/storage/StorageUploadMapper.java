package com.shiguang.storage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StorageUploadMapper extends BaseMapper<StorageUpload> {

    @Select("SELECT * FROM storage_upload WHERE object_name = #{objectName} FOR UPDATE")
    StorageUpload lockByObjectName(@Param("objectName") String objectName);

    @Update("""
            UPDATE storage_upload SET deleted = 1
            WHERE object_name = #{objectName} AND uploader_id = #{ownerId}
              AND NOT EXISTS (SELECT 1 FROM user WHERE BINARY avatar_url = #{objectName})
              AND NOT EXISTS (
                SELECT 1 FROM post
                WHERE BINARY video_object = #{objectName} OR BINARY cover_object = #{objectName}
                   OR (BINARY source_object = #{objectName} AND status IN ('PROCESSING', 'FAILED'))
                   OR JSON_CONTAINS(images, JSON_QUOTE(#{objectName})))
              AND NOT EXISTS (
                SELECT 1 FROM comment
                WHERE JSON_CONTAINS(IF(JSON_VALID(image_urls), image_urls, JSON_ARRAY()), JSON_QUOTE(#{objectName})))
              AND NOT EXISTS (
                SELECT 1 FROM private_message
                WHERE JSON_CONTAINS(IF(JSON_VALID(image_urls), image_urls, JSON_ARRAY()), JSON_QUOTE(#{objectName})))
            """)
    int markDeletedIfUnreferenced(@Param("ownerId") Long ownerId, @Param("objectName") String objectName);
}
