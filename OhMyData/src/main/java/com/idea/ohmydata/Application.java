package com.idea.ohmydata;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Application.class, args);

    }


    @Bean
    public ServletRegistrationBean servletRegistrationBean(ODataServlet servlet) {
        return new ServletRegistrationBean(servlet, "/*");
    }



}
