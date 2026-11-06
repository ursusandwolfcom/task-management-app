package com.example.taskmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.taskmanagement.mapper.ResponseMapper;
import com.example.taskmanagement.repository.UserRepository;
import com.example.taskmanagement.security.JwtService;
import com.example.taskmanagement.service.EmailService;
import com.example.taskmanagement.service.ProjectService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class BeanLifecycleTest extends ApiTestSupport {
    @Autowired
    private ConfigurableApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void statelessComponentsAreContainerSingletonsAndAdviceRemainsActive() {
        assertSingleton(ProjectService.class);
        assertSingleton(EmailService.class);
        assertSingleton(ResponseMapper.class);
        assertSingleton(UserRepository.class);
        assertSingleton(JwtService.class);
        assertSingleton(JwtEncoder.class);
        assertThat(AopUtils.isAopProxy(context.getBean(ProjectService.class))).isTrue();
        assertThat(AopUtils.isAopProxy(context.getBean(EmailService.class))).isTrue();
        assertThat(AopUtils.isAopProxy(context.getBean(UserRepository.class))).isTrue();
        ConfigurableListableBeanFactory beans = context.getBeanFactory();
        assertThat(beans.getDependentBeans("jwtSigningKey")).contains("jwtEncoder", "jwtDecoder");
        assertThat(beans.getDependentBeans("jwtEncoder")).contains("jwtService");
    }

    @Test
    void liquibaseInitializedSchemaBeforeJpaValidationAndRepositoryUse() {
        assertThat(context.getBean(EntityManagerFactory.class).isOpen()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from public.databasechangelog", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select id from public.databasechangelog", String.class))
                .isEqualTo("course-001");
        assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    private void assertSingleton(Class<?> type) {
        Object instance = context.getBean(type);
        assertThat(context.getBean(type)).isSameAs(instance);
        for (String name : context.getBeanNamesForType(type)) {
            assertThat(context.isSingleton(name)).as(name).isTrue();
        }
    }
}
