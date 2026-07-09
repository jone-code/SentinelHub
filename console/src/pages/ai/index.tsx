import { Button, Card, Col, Row, Statistic, Table, Tag, message } from 'antd';
import { useEffect, useState } from 'react';
import { api, ApiEnvelope, PageData } from '../../api/client';

interface Overview {
  open: number;
  total: number;
}

interface Insight {
  id: string;
  insight_type: string;
  severity: string;
  title: string;
  summary: string;
  hostname: string | null;
  agent_id: string | null;
  status: string;
  created_at: string;
}

const severityColors: Record<string, string> = {
  high: 'red',
  warning: 'orange',
  info: 'blue',
};

export default function Ai() {
  const [overview, setOverview] = useState<Overview>({ open: 0, total: 0 });
  const [insights, setInsights] = useState<Insight[]>([]);
  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [llmSummary, setLlmSummary] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    Promise.all([
      api.get<ApiEnvelope<Overview>>('/ai/overview'),
      api.get<ApiEnvelope<PageData<Insight>>>('/ai/insights', { params: { status: 'open' } }),
    ])
      .then(([overviewRes, insightsRes]) => {
        setOverview(overviewRes.data.data);
        setInsights(insightsRes.data.data.items);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const runAnalysis = async () => {
    setAnalyzing(true);
    try {
      const res = await api.post<ApiEnvelope<{ insights_created: number; llm_summary?: string }>>('/ai/analyze');
      message.success(`分析完成，新增 ${res.data.data.insights_created} 条洞察`);
      if (res.data.data.llm_summary) {
        setLlmSummary(res.data.data.llm_summary);
      }
      load();
    } finally {
      setAnalyzing(false);
    }
  };

  const resolve = async (id: string) => {
    await api.post(`/ai/insights/${id}/resolve`);
    message.success('已标记为已处理');
    load();
  };

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={8}>
          <Card loading={loading}>
            <Statistic title="待处理洞察" value={overview.open} />
          </Card>
        </Col>
        <Col span={8}>
          <Card loading={loading}>
            <Statistic title="洞察总数" value={overview.total} />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Button type="primary" onClick={runAnalysis} loading={analyzing}>
              运行安全分析
            </Button>
          </Card>
        </Col>
      </Row>

      {llmSummary && (
        <Card title="AI 摘要（LLM）" style={{ marginBottom: 24 }}>
          <p style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{llmSummary}</p>
        </Card>
      )}

      <Card title="安全洞察（规则引擎）" loading={loading}>
        <Table
          rowKey="id"
          dataSource={insights}
          pagination={false}
          columns={[
            { title: '标题', dataIndex: 'title' },
            {
              title: '严重级别',
              dataIndex: 'severity',
              render: (v: string) => <Tag color={severityColors[v] ?? 'default'}>{v}</Tag>,
            },
            { title: '类型', dataIndex: 'insight_type' },
            {
              title: '设备',
              render: (_, r) => r.hostname ?? r.agent_id ?? '—',
            },
            { title: '摘要', dataIndex: 'summary', ellipsis: true },
            {
              title: '操作',
              render: (_, row) => (
                <Button type="link" onClick={() => resolve(row.id)}>
                  标记已处理
                </Button>
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
}
