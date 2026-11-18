package com.sica.domain;

import java.util.UUID;

public record Session(UUID token, long userId) {}
