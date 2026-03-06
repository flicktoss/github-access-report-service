package com.githubaccessreport.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubCollaborator {

    private String login;
    private Map<String, Boolean> permissions;

    // permission hierarchy: admin > maintain > push > triage > pull
    public String getHighestPermission() {
        if (permissions == null || permissions.isEmpty()) {
            return "read";
        }
        if (Boolean.TRUE.equals(permissions.get("admin"))) return "admin";
        if (Boolean.TRUE.equals(permissions.get("maintain"))) return "maintain";
        if (Boolean.TRUE.equals(permissions.get("push"))) return "write";
        if (Boolean.TRUE.equals(permissions.get("triage"))) return "triage";
        if (Boolean.TRUE.equals(permissions.get("pull"))) return "read";
        return "read";
    }
}
