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
package com.wl4g.devops.iam.authc.credential.secure;

import javax.validation.constraints.NotNull;

import org.apache.shiro.authc.CredentialsException;

/**
 * IAM credentials securer
 * 
 * @author wangl.sir
 * @version v1.0 2019年1月16日
 * @since
 * @see {@link org.apache.shiro.crypto.hash.DefaultHashService#combine()}
 */
interface CredentialsSecurer {

	/**
	 * Encryption credentials
	 * 
	 * @param token
	 *            External input principal and credentials
	 * @param credentials
	 *            External input credentials
	 * @return
	 */
	default String signature(@NotNull CredentialsToken token) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Validation credentials
	 * 
	 * @param token
	 *            External input principal and credentials
	 * @param storedCredentials
	 *            Database storage credentials
	 * @return
	 */
	default boolean validate(@NotNull CredentialsToken token, @NotNull String storedCredentials)
			throws CredentialsException, RuntimeException {
		throw new UnsupportedOperationException();
	}

}