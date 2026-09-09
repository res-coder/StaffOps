package com.staffops.model;

import java.util.List;

public record IdentitySnapshot(List<String> previousNames, List<String> ipHistory, int possibleAlts) {}
