package com.hmdp.dto;

// 创建一个简单的 DTO 用于接收前端参数
    public  class ChatFormDTO {
        private Long memoryId;
        private String message;

        // getters and setters
        public Long getMemoryId() { return memoryId; }
        public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }