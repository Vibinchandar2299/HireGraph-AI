export interface GraphOverview {
  totalNodes: number;
  totalEdges: number;
  relationTypesCount: number;
  featureDimension: number;
  nodeCountsByType: Record<string, number>;
  relationTypes: string[];
}

export interface GraphNode {
  id: string;
  node_index?: number;
  type: string;
  is_center?: boolean;
  label?: string;
  displayName?: string;
  subtitle?: string;
  skillMatchStatus?: 'MATCHED' | 'MISSING' | string;
}

export interface GraphLink {
  source: string;
  source_type?: string;
  target: string;
  target_type?: string;
  relation: string;
  raw_relation?: string;
  relation_id?: number;
}

export interface SubgraphSummary {
  title: string;
  description: string;
  highlights: string[];
}

export interface SubgraphResponse {
  centerNode: GraphNode;
  nodesCount: number;
  linksCount: number;
  nodes: GraphNode[];
  links: GraphLink[];
  summary?: SubgraphSummary;
}
