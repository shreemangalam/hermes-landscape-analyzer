package com.hermes.api.dto;

import com.hermes.domain.NodeStatus;

/** One system in the health map, with its computed graph position. */
public record SystemHealthDto(
        String id,
        String name,
        String type,
        int businessCriticality,
        NodeStatus status,
        int fanIn,
        int fanOut,
        boolean junction,
        boolean leaf) {
}
