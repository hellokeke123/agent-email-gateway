package com.agentgateway.dto;

import com.agentgateway.entity.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoleDto {
    private Long id;
    private String name;
    private String description;

    public static RoleDto from(Role r) {
        return RoleDto.builder()
                .id(r.getId())
                .name(r.getName())
                .description(r.getDescription())
                .build();
    }
}
