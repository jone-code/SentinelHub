//! Shared policy JSON parsing for enforcement backends.

#[derive(Clone, Debug)]
pub struct ProcessRule {
    pub name: String,
    pub action: String,
}

pub fn parse_process_rules(policy_json: &str) -> Vec<ProcessRule> {
    let value: serde_json::Value = match serde_json::from_str(policy_json) {
        Ok(v) => v,
        Err(_) => return vec![],
    };

    let mut rules = Vec::new();

    let items = if let Some(arr) = value.as_array() {
        arr.clone()
    } else if let Some(rules_arr) = value.get("rules").and_then(|r| r.as_array()) {
        rules_arr.clone()
    } else {
        vec![]
    };

    for item in items {
        let channel = item.get("channel").and_then(|c| c.as_str()).unwrap_or("");
        if channel != "process_block" {
            continue;
        }
        let action = item
            .get("action")
            .and_then(|a| a.as_str())
            .unwrap_or("alert")
            .to_string();
        if let Some(patterns) = item.get("patterns").and_then(|p| p.as_array()) {
            for p in patterns {
                if let Some(s) = p.as_str() {
                    rules.push(ProcessRule {
                        name: s.to_string(),
                        action: action.clone(),
                    });
                }
            }
        }
    }

    let blacklist = value
        .pointer("/rules/software/config/blacklist")
        .or_else(|| value.pointer("/software/config/blacklist"))
        .and_then(|v| v.as_array());
    let sw_action = value
        .pointer("/rules/software/config/action")
        .or_else(|| value.pointer("/software/config/action"))
        .and_then(|v| v.as_str())
        .unwrap_or("alert")
        .to_string();
    if let Some(arr) = blacklist {
        for item in arr {
            if let Some(s) = item.as_str() {
                rules.push(ProcessRule {
                    name: s.to_string(),
                    action: sw_action.clone(),
                });
            }
        }
    }

    rules
}

pub fn blocked_process_names(rules: &[ProcessRule]) -> Vec<String> {
    let mut names = Vec::new();
    for rule in rules {
        if rule.action != "block" {
            continue;
        }
        let norm = normalize_name(&rule.name);
        if !norm.is_empty() && !names.iter().any(|n| n == &norm) {
            names.push(norm);
        }
    }
    names
}

fn normalize_name(name: &str) -> String {
    name.trim()
        .to_lowercase()
        .trim_end_matches(".exe")
        .to_string()
}
