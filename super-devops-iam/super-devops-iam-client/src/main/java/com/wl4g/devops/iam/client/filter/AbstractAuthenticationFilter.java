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
package com.wl4g.devops.iam.client.filter;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.Assert;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.apache.shiro.web.util.SavedRequest;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.wl4g.devops.common.constants.IAMDevOpsConstants.URI_AUTHENTICATOR;
import static com.wl4g.devops.common.utils.serialize.JacksonUtils.toJSONString;
import static com.wl4g.devops.common.utils.web.WebUtils2.safeEncodeURL;
import static com.wl4g.devops.common.web.RespBase.RetCode.OK;
import static com.wl4g.devops.common.web.RespBase.RetCode.UNAUTHC;
import static com.wl4g.devops.common.constants.IAMDevOpsConstants.CACHE_TICKET_C;
import static com.wl4g.devops.iam.common.config.AbstractIamProperties.DEFAULT_AUTHC_STATUS;
import static com.wl4g.devops.iam.common.config.AbstractIamProperties.DEFAULT_UNAUTHC_STATUS;
import static com.wl4g.devops.iam.common.utils.SessionBindings.bind;
import static com.wl4g.devops.iam.common.utils.Sessions.getSessionExpiredTime;
import static com.wl4g.devops.iam.common.utils.Sessions.getSessionId;
import static org.apache.commons.lang3.StringUtils.endsWith;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.shiro.util.Assert.hasText;

import com.wl4g.devops.common.exception.iam.InvalidGrantTicketException;
import com.wl4g.devops.common.exception.iam.UnauthenticatedException;
import com.wl4g.devops.common.exception.iam.UnauthorizedException;
import com.wl4g.devops.common.utils.Exceptions;
import com.wl4g.devops.common.utils.web.WebUtils2;
import com.wl4g.devops.common.utils.web.WebUtils2.ResponseType;
import com.wl4g.devops.common.web.RespBase;
import com.wl4g.devops.iam.client.authc.FastCasAuthenticationToken;
import com.wl4g.devops.iam.client.config.IamClientProperties;
import com.wl4g.devops.iam.client.context.ClientSecurityContext;
import com.wl4g.devops.iam.client.context.ClientSecurityCoprocessor;
import com.wl4g.devops.iam.common.cache.EnhancedCache;
import com.wl4g.devops.iam.common.cache.EnhancedKey;
import com.wl4g.devops.iam.common.cache.JedisCacheManager;
import com.wl4g.devops.iam.common.filter.IamAuthenticationFilter;

import java.io.IOException;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This filter validates the CAS service ticket to authenticate the user. It
 * must be configured on the URL recognized by the CAS server. For example, in
 * {@code shiro.ini}:
 * 
 * <pre>
 * [main]
 * casFilter = org.apache.shiro.cas.CasFilter
 * ...
 *
 * [urls]
 * /shiro-cas = casFilter
 * ...
 * </pre>
 * 
 * (example : http://host:port/mycontextpath/shiro-cas)
 *
 * @since 1.2
 */
public abstract class AbstractAuthenticationFilter<T extends AuthenticationToken> extends AuthenticatingFilter
		implements IamAuthenticationFilter {
	final public static String SAVE_GRANT_TICKET = AbstractAuthenticationFilter.class.getSimpleName() + ".GRANT_TICKET";

	/**
	 * What kind of URL request does not need to be remembered (i.e. using the
	 * default successUrl) when using the function of recording login successful
	 * callback URLs ? <br/>
	 * (For example, jump to IAM/login.html after executing logout)
	 */
	final public static String[] EXCLOUDE_SAVED_REDIRECT_URLS = { ("/" + LogoutAuthenticationFilter.NAME) };

	final protected Logger log = LoggerFactory.getLogger(getClass());

	final protected IamClientProperties config;

	/**
	 * Client security context handler.
	 */
	final protected ClientSecurityContext context;

	/**
	 * Client security processor.
	 */
	final protected ClientSecurityCoprocessor coprocessor;

	/**
	 * Using Distributed Cache to Ensure Concurrency Control under
	 * Mutilate-Node.
	 */
	final private EnhancedCache cache;

	public AbstractAuthenticationFilter(IamClientProperties config, ClientSecurityContext context,
			ClientSecurityCoprocessor coprocessor, JedisCacheManager cacheManager) {
		Assert.notNull(config, "'config' must not be null");
		Assert.notNull(context, "'context' must not be null");
		Assert.notNull(coprocessor, "'interceptor' must not be null");
		Assert.notNull(cacheManager, "'cacheManager' must not be null");
		this.config = config;
		this.context = context;
		this.coprocessor = coprocessor;
		this.cache = cacheManager.getEnhancedCache(CACHE_TICKET_C);
	}

	@Override
	protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) throws Exception {
		return createAuthenticationToken(WebUtils.toHttp(request), WebUtils.toHttp(response));
	}

	protected abstract T createAuthenticationToken(HttpServletRequest request, HttpServletResponse response) throws Exception;

	@Override
	protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
		return super.executeLogin(request, response);
	}

	@Override
	protected boolean onLoginSuccess(AuthenticationToken token, Subject subject, ServletRequest request, ServletResponse response)
			throws Exception {
		FastCasAuthenticationToken ftoken = (FastCasAuthenticationToken) token;
		Assert.notNull(ftoken.getCredentials(), "token.credentials(grant ticket) must not be null");

		// Grant ticket
		String grantTicket = (String) ftoken.getCredentials();

		/*
		 * Binding session => grantTicket. Synchronize with
		 * FastCasAuthorizingRealm#doGetAuthenticationInfo
		 */
		bind(SAVE_GRANT_TICKET, grantTicket);
		if (log.isDebugEnabled()) {
			log.debug("Authenticated bind grantTicket[{}], sessionId[{}]", grantTicket, getSessionId(subject));
		}

		/**
		 * Binding grantTicket => sessionId. Synchronize with
		 * IamClientSessionManager#getSessionId
		 */
		long expiredMs = getSessionExpiredTime();
		cache.put(new EnhancedKey(grantTicket, expiredMs), getSessionId(subject));

		// Determine success URL
		String successUrl = determineSuccessRedirectUrl(ftoken, subject, request, response);

		// Call Logon success handle.
		coprocessor.postAuthenticatingSuccess(ftoken, subject, request, response);

		// JSON response
		if (isJSONResponse(request)) {
			try {
				// Make logged response json.
				String logged = makeLoggedResponse(request, successUrl);
				if (log.isInfoEnabled()) {
					log.info("Authenticated response to - {}", logged);
				}
				WebUtils2.writeJson(WebUtils.toHttp(response), logged);
			} catch (IOException e) {
				log.error("Logged response json error", e);
			}
		}
		// Redirection
		else {
			if (log.isInfoEnabled()) {
				log.info("Authenticated redirect to - {}", successUrl);
			}
			WebUtils.issueRedirect(request, response, successUrl);
		}

		return false;
	}

	@Override
	protected boolean onLoginFailure(AuthenticationToken token, AuthenticationException ae, ServletRequest request,
			ServletResponse response) {
		Throwable cause = Exceptions.getRootCause(ae);
		if (cause != null) {
			if (cause instanceof RuntimeException) {
				log.error("Failed to caused by: {}", Exceptions.getMessage(cause));
			} else {
				log.error("Failed to authentication.", cause);
			}
		}

		// Failure redirect URL
		String failureRedirectUrl = makeFailureRedirectUrl(cause, WebUtils.toHttp(request));

		// Post handle of authenticate failure.
		coprocessor.postAuthenticatingFailure(token, ae, request, response);

		/*
		 * Only if the error is not authenticated, can it be redirected to the
		 * IAM server login page, otherwise the client will display the error
		 * page directly (to prevent unlimited redirection).
		 * See:xx.validation.AbstractBasedValidator#doGetRemoteValidation()
		 */
		if (cause == null || (cause instanceof InvalidGrantTicketException)) {
			// Response JSON message
			if (isJSONResponse(request)) {
				try {
					final String failMsg = makeFailedResponse(failureRedirectUrl, cause);
					if (log.isInfoEnabled()) {
						log.info("Failed response: {}", failMsg);
					}
					WebUtils2.writeJson(WebUtils.toHttp(response), failMsg);
				} catch (IOException e) {
					log.error("Response json error", e);
				}
			} else { // Redirects the login page direct.
				try {
					WebUtils.issueRedirect(request, response, failureRedirectUrl);
				} catch (IOException e) {
					log.error("Cannot redirect to failure url - {}", failureRedirectUrl, e);
				}
			}
		}
		// If it is an error caused by interface connection, etc.
		else {
			try {
				String errmsg = String.format("<b>Iam Server Internal Error</b><br/>%s", Exceptions.getMessage(cause));
				WebUtils.toHttp(response).sendError(HttpServletResponse.SC_BAD_GATEWAY, errmsg);
			} catch (IOException e) {
				log.error("Failed to response error", e);
			}
		}

		/*
		 * Termination of execution. Otherwise, DispatcherServlet will perform
		 * redirection and eventually result in an exception:Cannot call
		 * sendRedirect() after the response has been committed
		 */
		return false;
	}

	/**
	 * Determine is the JSON interactive strategy
	 * 
	 * @param request
	 * @return
	 */
	protected boolean isJSONResponse(ServletRequest request) {
		// Using dynamic parameter
		ResponseType respType = ResponseType.safeOf(WebUtils.getCleanParam(request, config.getParam().getResponseType()));
		if (log.isDebugEnabled()) {
			log.debug("Using response type:{}", respType);
		}
		return ResponseType.isJSONResponse(respType, WebUtils.toHttp(request));
	}

	/**
	 * Make logged-in response message.
	 * 
	 * @param request
	 *            Servlet request
	 * @param redirectUri
	 *            login success redirect URL
	 * @return
	 */
	private String makeLoggedResponse(ServletRequest request, String redirectUri) {
		hasText(redirectUri, "'redirectUri' must not be null");
		// Make message
		RespBase<String> resp = RespBase.create();
		resp.setCode(OK).setStatus(DEFAULT_AUTHC_STATUS).setMessage("Login successful");
		resp.getData().put(config.getParam().getRedirectUrl(), redirectUri);
		return toJSONString(resp);
	}

	/**
	 * Make login failed response message.
	 * 
	 * @param loginRedirectUrl
	 *            Login redirect URL
	 * @param err
	 *            Exception object
	 * @return
	 */
	private String makeFailedResponse(String loginRedirectUrl, Throwable err) {
		String errmsg = err != null ? err.getMessage() : "Not logged-in";
		// Make message
		RespBase<String> resp = RespBase.create();
		resp.setCode(UNAUTHC).setStatus(DEFAULT_UNAUTHC_STATUS).setMessage(errmsg);
		resp.getData().put(config.getParam().getRedirectUrl(), loginRedirectUrl);
		return toJSONString(resp);
	}

	/**
	 * Make failure redirect URL
	 * 
	 * @param cause
	 * @param request
	 * @return
	 */
	protected String makeFailureRedirectUrl(Throwable cause, HttpServletRequest request) {
		// Redirect URL
		String callbackRedirectUrl = new StringBuffer(WebUtils2.getRFCBaseURI(request, true)).append(URI_AUTHENTICATOR)
				.toString();

		// Redirect URL with callback
		StringBuffer fullRedirectUrl = new StringBuffer();

		// Unauthorized
		if (cause instanceof UnauthorizedException) {
			fullRedirectUrl.append(config.getUnauthorizedUri());
		} else if (cause instanceof UnauthenticatedException) { // Unauthenticated
			fullRedirectUrl.append(getLoginUrl());
			fullRedirectUrl.append("?").append(config.getParam().getApplication());
			fullRedirectUrl.append("=").append(config.getServiceName());
			fullRedirectUrl.append("&").append(config.getParam().getRedirectUrl());
			// Add custom parameters.
			String queryString = request.getQueryString();
			if (!isBlank(queryString)) {
				if (endsWith(callbackRedirectUrl, "?")) {
					callbackRedirectUrl += "&" + queryString;
				} else {
					callbackRedirectUrl += "?" + queryString;
				}
			}
			fullRedirectUrl.append("=").append(safeEncodeURL(callbackRedirectUrl));
		}

		return fullRedirectUrl.toString();
	}

	/**
	 * determine success redirect URL
	 * 
	 * @param token
	 * @param subject
	 * @param request
	 * @param response
	 * @return
	 */
	private String determineSuccessRedirectUrl(AuthenticationToken token, Subject subject, ServletRequest request,
			ServletResponse response) {
		String successUrl = getRememberUrl(WebUtils.toHttp(request));
		if (successUrl == null) {
			successUrl = config.getSuccessUri();
		}
		// Re-determine
		successUrl = context.determineLoginSuccessUrl(successUrl, token, subject, request, response);
		// Check and cleaning success url
		successUrl = WebUtils2.cleanURI(successUrl);
		Assert.notNull(successUrl, "'successUrl' must not be null");
		return successUrl;
	}

	/**
	 * Get remember last request URL
	 * 
	 * @param request
	 * @return
	 */
	private String getRememberUrl(HttpServletRequest request) {
		// Use remember redirect
		if (config.isUseRememberRedirect()) {
			SavedRequest savedReq = WebUtils.getAndClearSavedRequest(request);
			if (savedReq != null) {
				// URL excluding redirection remember
				if (!StringUtils.endsWithAny(savedReq.getRequestURI(), EXCLOUDE_SAVED_REDIRECT_URLS)) {
					return WebUtils2.getRFCBaseURI(request, false) + savedReq.getRequestUrl();
				}
			}
		}
		return null;
	}

	@Override
	public abstract String getName();

}