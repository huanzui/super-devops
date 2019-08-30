/*
 * Copyright 2017 ~ 2025 the original author or authors. <wanglsir@gmail.com, 983708408@qq.com>
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
package com.wl4g.devops.iam.captcha.verification;

import java.io.IOException;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;

import org.springframework.util.Assert;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.wl4g.devops.iam.verification.GraphBasedSecurityVerifier;

/**
 * Google KAPTCHA handler.
 * 
 * @author Wangl.sir <983708408@qq.com>
 * @version v1.0
 * @date 2018年12月28日
 * @since
 */
public class KaptchaSecurityVerifier extends GraphBasedSecurityVerifier<String> {

	private DefaultKaptcha kaptchaProducer;

	@Override
	public VerifyType verifyType() {
		return VerifyType.GRAPH_SIMPLE;
	}

	public KaptchaSecurityVerifier(DefaultKaptcha kaptchaProducer) {
		Assert.notNull(kaptchaProducer, "'kaptchaProducer' must not be null");
		this.kaptchaProducer = kaptchaProducer;
	}

	@Override
	protected void imageWrite(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, String verifyCode)
			throws IOException {
		ServletOutputStream out = response.getOutputStream();
		// Write the data out
		ImageIO.write(kaptchaProducer.createImage(verifyCode), "JPEG", out);
	}

}