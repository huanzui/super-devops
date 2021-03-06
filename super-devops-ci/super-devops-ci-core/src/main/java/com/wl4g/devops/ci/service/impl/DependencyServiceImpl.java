/*
 * Copyright 2017 ~ 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wl4g.devops.ci.service.impl;

import com.wl4g.devops.ci.config.CiCdProperties;
import com.wl4g.devops.ci.service.DependencyService;
import com.wl4g.devops.ci.service.ProjectService;
import com.wl4g.devops.ci.utils.GitUtils;
import com.wl4g.devops.ci.utils.SSHTool;
import com.wl4g.devops.common.bean.ci.Dependency;
import com.wl4g.devops.common.bean.ci.Project;
import com.wl4g.devops.common.bean.ci.TaskHistory;
import com.wl4g.devops.common.bean.ci.TaskSign;
import com.wl4g.devops.common.bean.ci.dto.TaskResult;
import com.wl4g.devops.dao.ci.DependencyDao;
import com.wl4g.devops.dao.ci.ProjectDao;
import com.wl4g.devops.dao.ci.TaskSignDao;
import com.wl4g.devops.shell.utils.ShellContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

import static com.wl4g.devops.common.constants.CiDevOpsConstants.TASK_LOCK_STATUS_LOCK;
import static com.wl4g.devops.common.constants.CiDevOpsConstants.TASK_LOCK_STATUS__UNLOCK;

/**
 * Dependency service implements
 *
 * @author Wangl.sir <983708408@qq.com>
 * @author vjay
 * @date 2019-05-22 11:39:00
 */
@Service
public class DependencyServiceImpl implements DependencyService {

	final protected Logger log = LoggerFactory.getLogger(getClass());

	@Autowired
	private CiCdProperties config;

	@Autowired
	private DependencyDao dependencyDao;

	@Autowired
	private ProjectDao projectDao;

	@Autowired
	private ProjectService projectService;

	@Autowired
	private TaskSignDao taskSignDao;

	/**
	 * maven install -- if it has dependency project , build dependency first
	 * 
	 * @param taskHistory
	 * @param dependency
	 * @param branch
	 * @param success
	 * @param result
	 * @param isDependency
	 * @throws Exception
	 */
	@Override
	public void build(TaskHistory taskHistory, Dependency dependency, String branch, TaskResult taskResult, boolean isDependency)
			throws Exception {
		Integer projectId = dependency.getProjectId();
		List<Dependency> dependencies = dependencyDao.getParentsByProjectId(projectId);
		if (dependencies != null && dependencies.size() > 0) {
			for (Dependency dep : dependencies) {
				String br = dep.getBranch();
				Dependency dependency1 = new Dependency(dep.getDependentId());
				dependency1.setId(dep.getId());
				// 如果依赖配置中有配置分支，则用配置的分支，若没有，则默认用打包项目的分支
				build(taskHistory, dependency1, StringUtils.isBlank(br) ? branch : br, taskResult, true);
			}
		}

		// Is Continue ? if fail then return
		if (!taskResult.isSuccess()) {
			return;
		}
		// ===== build start =====
		log.info("build start projectId={}", projectId);
		Project project = projectDao.selectByPrimaryKey(projectId);
		Assert.notNull(project, "project not exist");
		try {
			if (project.getLockStatus() != null && project.getLockStatus() == TASK_LOCK_STATUS_LOCK) { // 校验项目锁定状态
																										// ，锁定则无法继续
				throw new RuntimeException("project is lock , please check the project lock status");
			}
			projectService.updateLockStatus(projectId, TASK_LOCK_STATUS_LOCK);// 锁定项目，防止同一个项目同时build

			String path = config.getGitBasePath() + "/" + project.getProjectName();
			if (GitUtils.checkGitPahtExist(path)) {// 若果目录存在则:chekcout 分支 并 pull
				GitUtils.checkout(config.getCredentials(), path, branch);
				taskResult.getStringBuffer().append("project checkout success:").append(project.getProjectName()).append("\n");
			} else { // 若目录不存在: 则clone 项目并 checkout 对应分支
				GitUtils.clone(config.getCredentials(), project.getGitUrl(), path, branch);
				taskResult.getStringBuffer().append("project clone success:").append(project.getProjectName()).append("\n");
			}

			// save dependency git sha -- 保存依赖项目的sha，用于回滚时找回对应的 历史依赖项目
			if (isDependency) {
				TaskSign taskSign = new TaskSign();
				taskSign.setTaskId(taskHistory.getId());
				taskSign.setDependenvyId(dependency.getId());
				taskSign.setShaGit(GitUtils.getOldestCommitSha(path));
				taskSignDao.insertSelective(taskSign);
			}

			// run install command
			String installResult = mvnInstall(path, taskResult);

			// ===== build end =====
			taskResult.getStringBuffer().append(installResult);
		} finally {
			// finish then unlock the project
			projectService.updateLockStatus(projectId, TASK_LOCK_STATUS__UNLOCK);
		}
	}

	/**
	 * Rollback
	 * 
	 * @param taskHistory
	 * @param refTaskHistory
	 * @param dependency
	 * @param branch
	 * @param success
	 * @param result
	 * @param isDependency
	 * @throws Exception
	 */
	@Override
	public void rollback(TaskHistory taskHistory, TaskHistory refTaskHistory, Dependency dependency, String branch,
			TaskResult taskResult, boolean isDependency) throws Exception {
		Integer projectId = dependency.getProjectId();
		List<Dependency> dependencies = dependencyDao.getParentsByProjectId(projectId);
		if (dependencies != null && dependencies.size() > 0) {
			for (Dependency dep : dependencies) {
				String br = dep.getBranch();
				Dependency dependency1 = new Dependency(dep.getDependentId());
				dependency1.setId(dep.getId());
				rollback(taskHistory, refTaskHistory, dependency1, StringUtils.isBlank(br) ? branch : br, taskResult, true);
			}
		}

		// Is Continue ? if fail then return
		if (!taskResult.isSuccess()) {
			return;
		}
		// ===== build start =====
		log.info("build start projectId={}", projectId);
		Project project = projectDao.selectByPrimaryKey(projectId);
		Assert.notNull(project, "project not exist");
		try {
			if (project.getLockStatus() == TASK_LOCK_STATUS_LOCK) {
				throw new RuntimeException("project is lock , please check the project lock status");
			}
			projectService.updateLockStatus(projectId, TASK_LOCK_STATUS_LOCK);

			String path = config.getGitBasePath() + "/" + project.getProjectName();

			String sha;
			if (isDependency) {
				TaskSign taskSign = taskSignDao.selectByDependencyIdAndTaskId(dependency.getId(), taskHistory.getRefId());
				Assert.notNull(taskSign, "not found taskSign");
				sha = taskSign.getShaGit();
			} else {
				sha = refTaskHistory.getShaGit();
			}

			if (GitUtils.checkGitPahtExist(path)) {
				GitUtils.roolback(config.getCredentials(), path, sha);
				taskResult.getStringBuffer().append("project rollback success:").append(project.getProjectName()).append("\n");
			} else {
				GitUtils.clone(config.getCredentials(), project.getGitUrl(), path, branch);
				taskResult.getStringBuffer().append("project clone success:").append(project.getProjectName()).append("\n");
				GitUtils.roolback(config.getCredentials(), path, sha);
				taskResult.getStringBuffer().append("project rollback success:").append(project.getProjectName()).append("\n");
			}

			//// save dependency git sha -- 保存依赖项目的sha，回滚时也要保存进该表
			if (isDependency) {
				TaskSign taskSign = new TaskSign();
				taskSign.setTaskId(taskHistory.getId());
				taskSign.setDependenvyId(dependency.getId());
				taskSign.setShaGit(GitUtils.getOldestCommitSha(path));
				taskSignDao.insertSelective(taskSign);
			}

			// run install command
			String installResult = mvnInstall(path, taskResult);

			// ===== build end =====
			taskResult.getStringBuffer().append(installResult); // just for show
																// in page
		} finally {
			// finish then unlock the project
			projectService.updateLockStatus(projectId, TASK_LOCK_STATUS__UNLOCK);
		}
	}

	/**
	 * Building (maven)
	 */
	private String mvnInstall(String path, TaskResult taskResult) throws Exception {
		// Execution mvn
		String command = "mvn -f " + path + "/pom.xml clean install -Dmaven.test.skip=true";
		return SSHTool.exec(command, inlog -> !ShellContextHolder.isInterruptIfNecessary(), taskResult);
	}

}