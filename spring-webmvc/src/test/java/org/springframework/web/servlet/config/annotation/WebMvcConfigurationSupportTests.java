/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.web.servlet.config.annotation;

import java.util.List;
import javax.servlet.http.HttpServletRequest;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.HttpEntity;
import org.springframework.mock.web.test.MockHttpServletRequest;
import org.springframework.mock.web.test.MockServletContext;
import org.springframework.stereotype.Controller;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.method.support.CompositeUriComponentsContributor;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;
import org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping;
import org.springframework.web.servlet.handler.ConversionServiceExposingInterceptor;
import org.springframework.web.servlet.handler.HandlerExceptionResolverComposite;
import org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.JsonViewResponseBodyInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;
import org.springframework.web.servlet.resource.ResourceUrlProviderExposingInterceptor;

import static org.junit.Assert.*;

/**
 * A test fixture with an {@link WebMvcConfigurationSupport} instance.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 */
public class WebMvcConfigurationSupportTests {

	private WebApplicationContext wac;


	@Before
	public void setUp() {
		AnnotationConfigWebApplicationContext cxt = new AnnotationConfigWebApplicationContext();
		cxt.setServletContext(new MockServletContext());
		cxt.register(TestConfig.class, ScopedController.class, ScopedProxyController.class);
		cxt.refresh();

		this.wac = cxt;
	}


	@Test
	public void requestMappingHandlerMapping() throws Exception {
		RequestMappingHandlerMapping handlerMapping = this.wac.getBean(RequestMappingHandlerMapping.class);
		assertEquals(0, handlerMapping.getOrder());

		HandlerExecutionChain chain = handlerMapping.getHandler(new MockHttpServletRequest("GET", "/"));
		assertNotNull(chain);
		assertNotNull(chain.getInterceptors());
		assertEquals(ConversionServiceExposingInterceptor.class, chain.getInterceptors()[0].getClass());

		chain = handlerMapping.getHandler(new MockHttpServletRequest("GET", "/scoped"));
		assertNotNull(chain);

		chain = handlerMapping.getHandler(new MockHttpServletRequest("GET", "/scopedProxy"));
		assertNotNull(chain);
	}

	@Test
	public void emptyViewControllerHandlerMapping() {
		AbstractHandlerMapping handlerMapping = this.wac.getBean(
				"viewControllerHandlerMapping", AbstractHandlerMapping.class);

		assertNotNull(handlerMapping);
		assertEquals(Integer.MAX_VALUE, handlerMapping.getOrder());
		assertTrue(handlerMapping.getClass().getName().endsWith("EmptyHandlerMapping"));
	}

	@Test
	public void beanNameHandlerMapping() throws Exception {
		BeanNameUrlHandlerMapping handlerMapping = this.wac.getBean(BeanNameUrlHandlerMapping.class);
		assertEquals(2, handlerMapping.getOrder());

		HttpServletRequest request = new MockHttpServletRequest("GET", "/testController");
		HandlerExecutionChain chain = handlerMapping.getHandler(request);

		assertNotNull(chain.getInterceptors());
		assertEquals(3, chain.getInterceptors().length);
		assertEquals(ConversionServiceExposingInterceptor.class, chain.getInterceptors()[1].getClass());
		assertEquals(ResourceUrlProviderExposingInterceptor.class, chain.getInterceptors()[2].getClass());
	}

	@Test
	public void emptyResourceHandlerMapping() {
		AbstractHandlerMapping handlerMapping = this.wac.getBean(
				"resourceHandlerMapping", AbstractHandlerMapping.class);

		assertNotNull(handlerMapping);
		assertEquals(Integer.MAX_VALUE, handlerMapping.getOrder());
		assertTrue(handlerMapping.getClass().getName().endsWith("EmptyHandlerMapping"));
	}

	@Test
	public void emptyDefaultServletHandlerMapping() {
		AbstractHandlerMapping handlerMapping = this.wac.getBean(
				"defaultServletHandlerMapping", AbstractHandlerMapping.class);

		assertNotNull(handlerMapping);
		assertEquals(Integer.MAX_VALUE, handlerMapping.getOrder());
		assertTrue(handlerMapping.getClass().getName().endsWith("EmptyHandlerMapping"));
	}

	@Test
	public void requestMappingHandlerAdapter() throws Exception {
		RequestMappingHandlerAdapter adapter = this.wac.getBean(RequestMappingHandlerAdapter.class);
		assertEquals(9, adapter.getMessageConverters().size());

		ConfigurableWebBindingInitializer initializer = (ConfigurableWebBindingInitializer) adapter.getWebBindingInitializer();
		assertNotNull(initializer);

		ConversionService conversionService = initializer.getConversionService();
		assertNotNull(conversionService);
		assertTrue(conversionService instanceof FormattingConversionService);

		Validator validator = initializer.getValidator();
		assertNotNull(validator);
		assertTrue(validator instanceof LocalValidatorFactoryBean);

		DirectFieldAccessor fieldAccessor = new DirectFieldAccessor(adapter);
		List<Object> interceptors = (List<Object>) fieldAccessor.getPropertyValue("responseBodyInterceptors");
		assertEquals(1, interceptors.size());
		assertEquals(JsonViewResponseBodyInterceptor.class, interceptors.get(0).getClass());
	}

	@Test
	public void uriComponentsContributor() throws Exception {
		CompositeUriComponentsContributor uriComponentsContributor = this.wac.getBean(
				MvcUriComponentsBuilder.MVC_URI_COMPONENTS_CONTRIBUTOR_BEAN_NAME,
				CompositeUriComponentsContributor.class);

		assertNotNull(uriComponentsContributor);
	}

	@Test
	public void handlerExceptionResolver() throws Exception {
		HandlerExceptionResolverComposite compositeResolver =
			this.wac.getBean("handlerExceptionResolver", HandlerExceptionResolverComposite.class);

		assertEquals(0, compositeResolver.getOrder());

		List<HandlerExceptionResolver> expectedResolvers = compositeResolver.getExceptionResolvers();

		assertEquals(ExceptionHandlerExceptionResolver.class, expectedResolvers.get(0).getClass());
		assertEquals(ResponseStatusExceptionResolver.class, expectedResolvers.get(1).getClass());
		assertEquals(DefaultHandlerExceptionResolver.class, expectedResolvers.get(2).getClass());

		ExceptionHandlerExceptionResolver eher = (ExceptionHandlerExceptionResolver) expectedResolvers.get(0);
		assertNotNull(eher.getApplicationContext());

		DirectFieldAccessor fieldAccessor = new DirectFieldAccessor(eher);
		List<Object> interceptors = (List<Object>) fieldAccessor.getPropertyValue("responseBodyInterceptors");
		assertEquals(1, interceptors.size());
		assertEquals(JsonViewResponseBodyInterceptor.class, interceptors.get(0).getClass());
	}


	@EnableWebMvc
	@Configuration
	public static class TestConfig {

		@Bean(name="/testController")
		public TestController testController() {
			return new TestController();
		}
	}


	@Controller
	public static class TestController {

		@RequestMapping("/")
		public void handle() {
		}

		@RequestMapping("/foo/{id}/bar/{date}")
		public HttpEntity<Void> methodWithTwoPathVariables(@PathVariable Integer id,
				@DateTimeFormat(iso = ISO.DATE) @PathVariable DateTime date) {
			return null;
		}
	}


	@Controller
	@Scope("prototype")
	public static class ScopedController {

		@RequestMapping("/scoped")
		public void handle() {
		}
	}


	@Controller
	@Scope(value="prototype", proxyMode=ScopedProxyMode.TARGET_CLASS)
	public static class ScopedProxyController {

		@RequestMapping("/scopedProxy")
		public void handle() {
		}
	}

}
