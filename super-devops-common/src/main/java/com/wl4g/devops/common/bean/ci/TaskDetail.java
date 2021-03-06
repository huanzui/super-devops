package com.wl4g.devops.common.bean.ci;

import java.io.Serializable;

import com.wl4g.devops.common.bean.BaseBean;

public class TaskDetail extends BaseBean implements Serializable {

	private static final long serialVersionUID = 381411777614066880L;

	private Integer taskId;

	private Integer instanceId;

	public Integer getTaskId() {
		return taskId;
	}

	public void setTaskId(Integer taskId) {
		this.taskId = taskId;
	}

	public Integer getInstanceId() {
		return instanceId;
	}

	public void setInstanceId(Integer instanceId) {
		this.instanceId = instanceId;
	}
}