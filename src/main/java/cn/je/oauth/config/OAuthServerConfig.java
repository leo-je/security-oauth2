package cn.je.oauth.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;
import org.springframework.security.oauth2.provider.token.TokenEnhancerChain;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;

import cn.je.oauth.service.UserService;

@Configuration
@EnableAuthorizationServer
public class OAuthServerConfig extends AuthorizationServerConfigurerAdapter {

	@Autowired
	AuthenticationManager authenticationManager;
	@Autowired
	RedisConnectionFactory redisConnectionFactory;

	@Autowired
	UserService userService;

	// @Autowired
	// private DataSource dataSource;

	private static final String DEMO_RESOURCE_ID = "order";

	@Override
	public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
		// 配置两个客户端,一个用于password认证一个用于client认证
		// @formatter:off
		clients//.jdbc(dataSource)
				.inMemory().withClient("client_1")
				.resourceIds(DEMO_RESOURCE_ID)
				.authorizedGrantTypes("client_credentials", "refresh_token")
				.scopes("select")
				.authorities("client")
				// security 4
				// .secret("123456")
				// security 5
				.secret(passwordEncoder().encode("123456"))
				.and().withClient("client_2")
				.resourceIds(DEMO_RESOURCE_ID)
				.authorizedGrantTypes("password", "refresh_token")
				.scopes("select").authorities("client")
				// security 4
				// .secret("123456")
				// security 5
				.secret(passwordEncoder().encode("123456"))
				// true 直接跳转到客户端页面，false 跳转到用户确认授权页面
				.autoApprove(true);;
		// @formatter:on
	}

	@Override
	public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
		// @formatter:off
		TokenEnhancerChain tokenEnhancerChain = new TokenEnhancerChain();
		tokenEnhancerChain.setTokenEnhancers(Arrays.asList(tokenEnhancer(), jwtAccessTokenConverter()));
		endpoints
				//.tokenStore(redisTokenStore())
				.authenticationManager(authenticationManager)
				// refresh_token需要userDetailsService
				.reuseRefreshTokens(false)
				.tokenEnhancer(tokenEnhancerChain)
				.userDetailsService(userService);
		DefaultTokenServices df = tokenServices();
		df.setTokenEnhancer(endpoints.getTokenEnhancer());
		//df.setTokenStore(endpoints.getTokenStore());
		endpoints.tokenServices(df);
		// @formatter:on
	}

	@Bean
	@Primary
	public DefaultTokenServices tokenServices() {
		DefaultTokenServices defaultTokenServices = new DefaultTokenServices();
		// 是否支持refresh_token
		defaultTokenServices.setSupportRefreshToken(true);
		defaultTokenServices.setTokenStore(redisTokenStore());
		// access_token过期时间 60*10s
		defaultTokenServices.setAccessTokenValiditySeconds(60 * 10);
		// refresh_token过期时间 60*60*24 s
		defaultTokenServices.setRefreshTokenValiditySeconds(60 * 60 * 24);
		return defaultTokenServices;
	}

	@Override
	public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
		security.allowFormAuthenticationForClients()
				// 获取JWt加密key: /oauth/token_key 采用RSA非对称加密时候使用。对称加密禁止访问
				// .tokenKeyAccess("isAuthenticated()")
				.checkTokenAccess("permitAll()");
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public JwtAccessTokenConverter jwtAccessTokenConverter() {
		JwtAccessTokenConverter jwtAccessTokenConverter = new JwtAccessTokenConverter();
		jwtAccessTokenConverter.setSigningKey("123");
		return jwtAccessTokenConverter;
	}

	/**
	 * 持久化token
	 */
	@Bean
	public TokenStore redisTokenStore() {
		// 1.redis持久化
		RedisTokenStore tokenStore = new RedisTokenStore(redisConnectionFactory);
		tokenStore.setPrefix("ouath_demo_");
		// 2.不进行持久化
		JwtTokenStore jwtTokenStore = new JwtTokenStore(jwtAccessTokenConverter());
		return jwtTokenStore;
	}

	/**
	 * jwt 生成token 定制化处理
	 * 
	 * @return TokenEnhancer
	 */
	@Bean
	public TokenEnhancer tokenEnhancer() {
		return (accessToken, authentication) -> {
			final Map<String, Object> additionalInfo = new HashMap<>(1);
			additionalInfo.put("license", "oauth demo");
			((DefaultOAuth2AccessToken) accessToken).setAdditionalInformation(additionalInfo);
			return accessToken;
		};
	}

}
