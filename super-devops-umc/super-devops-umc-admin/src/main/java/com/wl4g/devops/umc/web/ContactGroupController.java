package com.wl4g.devops.umc.web;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.wl4g.devops.common.bean.scm.CustomPage;
import com.wl4g.devops.common.bean.umc.AlarmContactGroup;
import com.wl4g.devops.common.web.BaseController;
import com.wl4g.devops.common.web.RespBase;
import com.wl4g.devops.dao.umc.AlarmContactGroupDao;
import com.wl4g.devops.umc.service.ContactGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author vjay
 * @date 2019-08-05 11:44:00
 */
@RestController
@RequestMapping("/contactGroup")
public class ContactGroupController extends BaseController {

    @Autowired
    private AlarmContactGroupDao alarmContactGroupDao;

    @Autowired
    private ContactGroupService contactGroupService;

    @RequestMapping(value = "/list")
    public RespBase<?> list(String name, CustomPage customPage) {
        log.info("into ContactGroupController.list prarms::"+ "name = {} , customPage = {} ", name, customPage );
        RespBase<Object> resp = RespBase.create();
        Integer pageNum = null != customPage.getPageNum() ? customPage.getPageNum() : 1;
        Integer pageSize = null != customPage.getPageSize() ? customPage.getPageSize() : 5;
        Page page = PageHelper.startPage(pageNum, pageSize, true);
        List<AlarmContactGroup> list = alarmContactGroupDao.list(name);
        customPage.setPageNum(pageNum);
        customPage.setPageSize(pageSize);
        customPage.setTotal(page.getTotal());
        resp.getData().put("page", customPage);
        resp.getData().put("list", list);
        return resp;
    }

    @RequestMapping(value = "/save")
    public RespBase<?> save(AlarmContactGroup alarmContactGroup) {
        log.info("into ContactGroupController.save prarms::"+ "alarmContactGroup = {} ", alarmContactGroup );
        RespBase<Object> resp = RespBase.create();
        contactGroupService.save(alarmContactGroup);
        return resp;
    }


    @RequestMapping(value = "/del")
    public RespBase<?> del(Integer id) {
        log.info("into ContactController.del prarms::"+ "id = {} ", id );
        RespBase<Object> resp = RespBase.create();
        contactGroupService.del(id);
        return resp;
    }

    @RequestMapping(value = "/all")
    public RespBase<?> all() {
        RespBase<Object> resp = RespBase.create();
        List<AlarmContactGroup> list = alarmContactGroupDao.list(null);
        resp.getData().put("list", list);
        return resp;
    }





}
