package com.app.n8n.model

enum class ServerState {
    NOT_INSTALLED,
    EXTRACTING,
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}
