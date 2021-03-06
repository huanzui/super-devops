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
package com.wl4g.devops.iam.authc;

import org.springframework.util.Assert;

import com.wl4g.devops.iam.common.authc.AbstractIamAuthenticationToken;

/**
 * SMS authentication token
 * 
 * @author Wangl.sir <983708408@qq.com>
 * @version v1.0
 * @date 2018年11月19日
 * @since
 */
public class SmsAuthenticationToken extends AbstractIamAuthenticationToken {
	private static final long serialVersionUID = 8587329689973009598L;

	/**
	 * Principal(e.g. user-name or mobile number etc)
	 */
	final private String principal;

	/**
	 * Dynamic verification code
	 */
	final private String verifyCode;

	final private Action action;

	public SmsAuthenticationToken() {
		this.principal = null;
		this.verifyCode = null;
		this.action = null;
	}

	public SmsAuthenticationToken(final String remoteHost, final String action, final String principal, final String verifyCode) {
		super(remoteHost);
		Assert.hasText(principal, "Dynamic principal must not be empty");
		Assert.hasText(verifyCode, "Dynamic credentials must not be empty");
		this.action = Action.of(action);
		this.principal = principal;
		this.verifyCode = verifyCode;
	}

	@Override
	public Object getPrincipal() {
		return principal;
	}

	@Override
	public Object getCredentials() {
		return verifyCode;
	}

	public Action getAction() {
		return action;
	}

	/**
	 * SMS authentication action
	 * 
	 * @author Wangl.sir <983708408@qq.com>
	 * @version v1.0 2019年4月19日
	 * @since
	 */
	public static enum Action {

		/**
		 * SMS login action type.
		 */
		LOGIN,

		/**
		 * SMS bind action type.
		 */
		BIND,

		/**
		 * SMS unbind action type.
		 */
		UNBIND;

		/**
		 * Converter string to {@link Action}
		 * 
		 * @param action
		 * @return
		 */
		public static Action of(String action) {
			Action wh = safeOf(action);
			if (wh == null) {
				throw new IllegalArgumentException(String.format("Illegal action '%s'", action));
			}
			return wh;
		}

		/**
		 * Safe converter string to {@link Action}
		 * 
		 * @param action
		 * @return
		 */
		public static Action safeOf(String action) {
			for (Action t : values()) {
				if (String.valueOf(action).equalsIgnoreCase(t.name())) {
					return t;
				}
			}
			return null;
		}

	}

}