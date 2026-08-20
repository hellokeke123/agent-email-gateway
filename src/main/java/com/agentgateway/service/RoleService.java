package com.agentgateway.service;

import com.agentgateway.entity.Role;
import com.agentgateway.exception.ApiException;
import com.agentgateway.mapper.RoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 角色 CRUD（软删除） */
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final int MAX_DESCRIPTION_LENGTH = 16_000;

    private final RoleMapper roleMapper;

    public List<Role> listAll() {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .eq(Role::getDeleted, false)
                .orderByAsc(Role::getId));
    }

    public List<Role> listEnabled() {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .eq(Role::getDeleted, false)
                .eq(Role::getEnabled, true)
                .orderByAsc(Role::getId));
    }

    public Role getOrThrow(Long id) {
        Role r = roleMapper.selectById(id);
        if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
            throw ApiException.notFound("角色不存在");
        }
        return r;
    }

    public Role getEnabledOrThrow(Long id) {
        Role r = getOrThrow(id);
        if (!Boolean.TRUE.equals(r.getEnabled())) {
            throw ApiException.badRequest("角色未启用");
        }
        return r;
    }

    public void create(String name, String description) {
        validateName(name);
        validateDescription(description);
        checkNameUnique(name, null);
        Role r = new Role();
        r.setName(name.trim());
        r.setDescription(description);
        r.setEnabled(true);
        r.setDeleted(false);
        roleMapper.insert(r);
    }

    public void update(Long id, String name, String description, Boolean enabled) {
        Role r = getOrThrow(id);
        if (name != null && !name.isBlank()) {
            String n = name.trim();
            if (!n.equals(r.getName())) {
                validateName(n);
                checkNameUnique(n, id);
                r.setName(n);
            }
        }
        if (description != null) {
            validateDescription(description);
            r.setDescription(description);
        }
        if (enabled != null) {
            r.setEnabled(enabled);
        }
        roleMapper.updateById(r);
    }

    public void softDelete(Long id) {
        Role r = getOrThrow(id);
        r.setDeleted(true);
        r.setDeletedAt(LocalDateTime.now());
        roleMapper.updateById(r);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("角色名不能为空");
        }
        if (name.trim().length() > 100) {
            throw ApiException.badRequest("角色名不能超过 100 字符");
        }
    }

    private void validateDescription(String description) {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw ApiException.badRequest("角色描述不能超过 16000 字符");
        }
    }

    private void checkNameUnique(String name, Long excludeId) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<Role>()
                .eq(Role::getName, name.trim())
                .eq(Role::getDeleted, false)
                .ne(excludeId != null, Role::getId, excludeId));
        if (count > 0) {
            throw ApiException.badRequest("角色名已存在");
        }
    }
}
