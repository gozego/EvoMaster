package com.foo.rest.examples.spring.authenticatedswaggeraccesswithoutauthentication;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@EnableWebSecurity
public class AuthenticatedSwaggerApplicationWithoutAuthenticationWebSecurityConfig extends WebSecurityConfigurerAdapter {

    // security configuration
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                //  .antMatchers("/v2/api-docs").permitAll()
                .antMatchers("/v2/api-docs").hasRole("AUTH_USER")
                .antMatchers(HttpMethod.GET, "/endpoint1").permitAll()
                .antMatchers(HttpMethod.GET, "/endpoint2").hasAnyRole("AUTH_USER")
                .anyRequest().authenticated()
                .and()
                .httpBasic()
                .and()
                .csrf().disable();
    }

}

