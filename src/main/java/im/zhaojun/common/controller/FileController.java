package im.zhaojun.common.controller;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.URLUtil;
import im.zhaojun.common.annotation.CheckStorageStrategyInit;
import im.zhaojun.common.exception.SearchDisableException;
import im.zhaojun.common.model.constant.ZFileConstant;
import im.zhaojun.common.model.dto.FileItemDTO;
import im.zhaojun.common.model.dto.ResultBean;
import im.zhaojun.common.model.dto.SiteConfigDTO;
import im.zhaojun.common.model.dto.SystemConfigDTO;
import im.zhaojun.common.service.AbstractFileService;
import im.zhaojun.common.service.FileAsyncCacheService;
import im.zhaojun.common.service.SystemConfigService;
import im.zhaojun.common.service.SystemService;
import im.zhaojun.common.util.FileComparator;
import im.zhaojun.common.util.HttpUtil;
import im.zhaojun.common.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 前台文件管理
 * @author zhaojun
 */
@RequestMapping("/api")
@RestController
public class FileController {

    @Resource
    private SystemService systemService;

    @Resource
    private SystemConfigService systemConfigService;

    @Resource
    private FileAsyncCacheService fileAsyncCacheService;

    /**
     * 滚动加载每页条数.
     */
    private static final Integer PAGE_SIZE = 30;

    @CheckStorageStrategyInit
    @GetMapping("/list")
    public ResultBean list(@RequestParam(defaultValue = "/") String path,
                           @RequestParam(defaultValue = "name") String sortBy,
                           @RequestParam(defaultValue = "asc") String order,
                           @RequestParam(required = false) String password,
                           @RequestParam(defaultValue = "1") Integer page) throws Exception {
        AbstractFileService fileService = systemConfigService.getCurrentFileService();
        List<FileItemDTO> fileItemList = fileService.fileList(StringUtils.removeDuplicateSeparator("/" + path + "/"));
        for (FileItemDTO fileItemDTO : fileItemList) {
            if (ZFileConstant.PASSWORD_FILE_NAME.equals(fileItemDTO.getName())
                && !HttpUtil.getTextContent(fileItemDTO.getUrl()).equals(password)) {
                if (password != null && !"".equals(password)) {
                    return ResultBean.error("密码错误.");
                }
                return ResultBean.error("此文件夹需要密码.", ResultBean.REQUIRED_PASSWORD);
            }
        }

        List<FileItemDTO> sortedPagingData = getSortedPagingData(fileItemList, page);
        return ResultBean.successData(sortedPagingData);
    }


    /**
     * 获取系统配置信息和当前页的标题, 文件头, 文件尾信息
     * @param path          路径
     */
    @CheckStorageStrategyInit
    @GetMapping("/config")
    public ResultBean getConfig(String path) throws Exception {
        SiteConfigDTO config = systemService.getConfig(StringUtils.removeDuplicateSeparator("/" + path + "/"));
        config.setSystemConfigDTO(systemConfigService.getSystemConfig());
        return ResultBean.successData(config);
    }


    @CheckStorageStrategyInit
    @GetMapping("/search")
    public ResultBean search(@RequestParam(value = "name", defaultValue = "/") String name,
                             @RequestParam(defaultValue = "name") String sortBy,
                             @RequestParam(defaultValue = "asc") String order,
                             @RequestParam(defaultValue = "1") Integer page) {
        AbstractFileService fileService = systemConfigService.getCurrentFileService();
        SystemConfigDTO systemConfigDTO = systemConfigService.getSystemConfig();
        if (BooleanUtil.isFalse(systemConfigDTO.getSearchEnable())) {
            throw new SearchDisableException("搜索功能未开启");
        }
        if (!fileAsyncCacheService.isCacheFinish()) {
            throw new SearchDisableException("搜索功能缓存预热中, 请稍后再试");
        }
        List<FileItemDTO> fileItemList = fileService.search(URLUtil.decode(name));
        List<FileItemDTO> sortedPagingData = getSortedPagingData(fileItemList, page);
        return ResultBean.successData(sortedPagingData);
    }


    /**
     * 过滤文件列表, 不显示密码, 头部和尾部文件.
     */
    private void filterFileList(List<FileItemDTO> fileItemList) {
        if (fileItemList == null) {
            return;
        }

        fileItemList.removeIf(fileItem -> ZFileConstant.PASSWORD_FILE_NAME.equals(fileItem.getName())
                || ZFileConstant.HEADER_FILE_NAME.equals(fileItem.getName()));
    }


    private List<FileItemDTO> getSortedPagingData(List<FileItemDTO> fileItemList, Integer page) {
        ArrayList<FileItemDTO> copy = new ArrayList<>(Arrays.asList(new FileItemDTO[fileItemList.size()]));
        Collections.copy(copy, fileItemList);

        // 排序, 先按照文件类型比较, 文件夹在前, 文件在后, 然后根据 sortBy 字段排序, 默认为升序;
        copy.sort(new FileComparator());
        filterFileList(copy);

        int total = copy.size();
        int totalPage = (total + PAGE_SIZE - 1) / PAGE_SIZE;

        if (page > totalPage) {
            return new ArrayList<>();
        }

        int start = (page - 1) * PAGE_SIZE;
        int end = page * PAGE_SIZE;
        end = Math.min(end, total);
        return new ArrayList<>(copy.subList(start, end));
    }


    /**
     * 获取指定路径下的文件信息内容
     * @param path      文件全路径
     * @return          该文件的名称, 路径, 大小, 下载地址等信息.
     */
    @CheckStorageStrategyInit
    @GetMapping("/directlink")
    public ResultBean directlink(String path) {
        AbstractFileService fileService = systemConfigService.getCurrentFileService();
        return ResultBean.successData(fileService.getFileItem(path));
    }
}
