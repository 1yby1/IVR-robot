package com.ivr.ai.flow;

import java.util.List;

public record GeneratedFlow(String graphJson,
                            String summary,
                            List<String> warnings,
                            String rawText) {
}
