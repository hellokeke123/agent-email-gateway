package com.agentgateway.service;

import com.agentgateway.entity.Role;
import com.agentgateway.exception.ApiException;
import com.agentgateway.mapper.RoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoleServiceTest {

    @Autowired
    RoleService roleService;
    @Autowired
    RoleMapper roleMapper;

    @Test
    void createAndUpdate_preserveLongMultilineChineseDescription() {
        String createdDescription = "职责：分析需求。\n输出：可执行方案。\n".repeat(200);
        roleService.create("长描述角色", createdDescription);

        Role created = roleService.listAll().get(0);
        assertEquals(createdDescription, roleMapper.selectById(created.getId()).getDescription());

        String updatedDescription = "步骤一：收集信息。\n步骤二：审核结果。\n".repeat(200);
        roleService.update(created.getId(), null, updatedDescription, null);

        assertEquals(updatedDescription, roleMapper.selectById(created.getId()).getDescription());
    }

    @Test
    void acceptsDescriptionAt16000Characters() {
        String description = "描".repeat(16_000);
        roleService.create("边界角色", description);

        Role created = roleService.listAll().get(0);
        assertEquals(description, roleMapper.selectById(created.getId()).getDescription());
    }

    @Test
    void rejectsDescriptionOver16000CharactersBeforePersistence() {
        String description = "描".repeat(16_001);

        ApiException exception = assertThrows(ApiException.class,
                () -> roleService.create("超长角色", description));

        assertEquals("角色描述不能超过 16000 字符", exception.getMessage());
        assertEquals(0, roleService.listAll().size());
    }

    @Test
    void updateRejectsDescriptionOver16000CharactersAndKeepsSavedValue() {
        String original = "原始描述\n";
        roleService.create("更新边界角色", original);
        Role role = roleService.listAll().get(0);

        assertThrows(ApiException.class,
                () -> roleService.update(role.getId(), null, "描".repeat(16_001), null));

        assertEquals(original, roleMapper.selectById(role.getId()).getDescription());
    }
}
