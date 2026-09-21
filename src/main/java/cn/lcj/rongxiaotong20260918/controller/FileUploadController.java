package cn.lcj.rongxiaotong20260918.controller;

import cn.lcj.rongxiaotong20260918.common.BusinessException;
import cn.lcj.rongxiaotong20260918.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传 Controller（独立，不继承 BaseController，公开接口）
 *
 * 存储位置：项目根目录下的 uploads/ 文件夹（application.yml 的 application.upload-path）
 * 访问 URL：http://localhost:8088/images/20260921/xxx.jpg
 *          （Spring MVC 静态资源映射 uploads/ 为 /images/**）
 */
@RestController
@RequestMapping("/api/common")
public class FileUploadController {

    /** 本地磁盘存储根目录 */
    @Value("${application.upload-path}")
    private String uploadPath;

    /** 静态资源访问前缀（与 yml 的 static-locations 对应） */
    private static final String URL_PREFIX = "/images/";

    /** 允许的图片扩展名 */
    private static final List<String> ALLOWED_EXT = List.of("jpg", "jpeg", "png", "gif", "webp");

    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }

        String filename = saveFile(file);
        String url = URL_PREFIX + filename;

        Map<String, String> data = new HashMap<>();
        data.put("url", url);
        return Result.success(data);
    }

    @PostMapping("/upload/multi")
    public Result<List<Map<String, String>>> uploadMulti(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new BusinessException(400, "请至少选择一个文件");
        }

        List<Map<String, String>> result = new ArrayList<>();
        for (MultipartFile file : files) {
            String filename = saveFile(file);
            Map<String, String> item = new HashMap<>();
            item.put("url", URL_PREFIX + filename);
            result.add(item);
        }
        return Result.success(result);
    }

    /**
     * 保存单个文件，返回相对路径（如 "20260921/uuid.jpg"）
     */
    private String saveFile(MultipartFile file) {
        // 1. 校验扩展名
        String originalName = file.getOriginalFilename();
        String ext = originalName == null ? "" : originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(400, "仅支持 " + ALLOWED_EXT + " 格式");
        }

        // 2. 按日期分子目录：uploads/20260921/
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        File dir = new File(uploadPath, dateDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException(500, "创建上传目录失败");
        }

        // 3. 生成唯一文件名：UUID.jpg
        String safeName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        File dest = new File(dir, safeName);

        // 4. 写盘
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败：" + e.getMessage());
        }

        return dateDir + "/" + safeName;
    }
}
