use serde::Deserialize;
use serde::Serialize;
use std::fs;
use std::path::Path;

#[derive(Deserialize)]
struct NacPolicy {
    min_compliance_score: i32,
    action_on_fail: String,
    isolate_vlan: Option<String>,
    enabled: Option<bool>,
}

#[derive(Serialize)]
pub struct NacResult {
    pub evaluated_at: String,
    pub access_state: String,
    pub reason: String,
    pub compliance_score: i32,
    pub min_compliance_score: i32,
    pub action_on_fail: String,
    pub isolate_vlan: Option<String>,
}

pub fn run(policy_path: &Path, compliance_score: i32) -> Result<NacResult, String> {
    let policy = load_policy(policy_path)?;
    if policy.enabled == Some(false) {
        return Ok(NacResult {
            evaluated_at: now_epoch(),
            access_state: "allowed".into(),
            reason: "policy_disabled".into(),
            compliance_score,
            min_compliance_score: policy.min_compliance_score,
            action_on_fail: policy.action_on_fail.clone(),
            isolate_vlan: policy.isolate_vlan.clone(),
        });
    }

    let min = policy.min_compliance_score;
    if compliance_score >= min {
        return Ok(NacResult {
            evaluated_at: now_epoch(),
            access_state: "allowed".into(),
            reason: "compliance_ok".into(),
            compliance_score,
            min_compliance_score: min,
            action_on_fail: policy.action_on_fail.clone(),
            isolate_vlan: policy.isolate_vlan.clone(),
        });
    }

    let access_state = match policy.action_on_fail.as_str() {
        "deny" | "denied" => "denied",
        "allow" => "allowed",
        _ => "restricted",
    };

    Ok(NacResult {
        evaluated_at: now_epoch(),
        access_state: access_state.into(),
        reason: "compliance_below_threshold".into(),
        compliance_score,
        min_compliance_score: min,
        action_on_fail: policy.action_on_fail.clone(),
        isolate_vlan: policy.isolate_vlan.clone(),
    })
}

fn load_policy(path: &Path) -> Result<NacPolicy, String> {
    let raw = fs::read_to_string(path).map_err(|e| e.to_string())?;
    serde_json::from_str(&raw).map_err(|e| e.to_string())
}

fn now_epoch() -> String {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs().to_string())
        .unwrap_or_else(|_| "0".into())
}
