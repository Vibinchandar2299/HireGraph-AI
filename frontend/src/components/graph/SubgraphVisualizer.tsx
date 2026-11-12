import React, { useEffect, useRef, useState } from 'react';
import cytoscape, { Core } from 'cytoscape';
import { ZoomIn, ZoomOut, Maximize2, RotateCcw, ExternalLink } from 'lucide-react';
import { SubgraphResponse, GraphNode } from '../../types/graph';
import { ENTITY_COLORS } from '../../utils/constants';

interface SubgraphVisualizerProps {
  data: SubgraphResponse;
  onNavigateToNode?: (type: string, id: string) => void;
  height?: string;
  showTechnicalLabels?: boolean;
}

export const SubgraphVisualizer: React.FC<SubgraphVisualizerProps> = ({
  data,
  onNavigateToNode,
  height = '540px',
  showTechnicalLabels = false,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const cyRef = useRef<Core | null>(null);
  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;
    setSelectedNode(null);

    // Transform API data to cytoscape elements
    const elements: any[] = [];

    // Nodes
    data.nodes.forEach((n) => {
      elements.push({
        group: 'nodes',
        data: {
          id: n.id,
          label: showTechnicalLabels ? n.id : (n.label || n.id),
          displayName: n.displayName || n.id,
          subtitle: n.subtitle || '',
          skillMatchStatus: n.skillMatchStatus || '',
          type: n.type.toLowerCase(),
          is_center: n.is_center ? 'true' : 'false',
        },
      });
    });

    // Links / Edges
    data.links.forEach((l, idx) => {
      elements.push({
        group: 'edges',
        data: {
          id: `edge-${idx}`,
          source: l.source,
          target: l.target,
          label: showTechnicalLabels ? (l.raw_relation || l.relation || '') : (l.relation || ''),
        },
      });
    });

    // Initialize Cytoscape
    const cy = cytoscape({
      container: containerRef.current,
      elements,
      style: [
        {
          selector: 'node',
          style: {
            label: 'data(label)',
            color: '#f8fafc',
            'font-family': 'Inter, sans-serif',
            'font-size': '10px',
            'text-wrap': 'wrap',
            'text-max-width': '90px',
            'text-valign': 'center',
            'text-halign': 'center',
            'text-outline-color': '#070b14',
            'text-outline-width': 2,
            'background-color': '#475569',
            width: 52,
            height: 52,
            'border-width': 2,
            'border-color': '#1e293b',
            'transition-property': 'background-color, border-color, width, height',
            'transition-duration': 0.2,
          },
        },
        ...Object.entries(ENTITY_COLORS).map(([type, color]) => ({
          selector: `node[type = "${type}"]`,
          style: {
            'background-color': color.hex,
            'border-color': color.border,
          },
        })),
        {
          selector: 'node[skillMatchStatus = "MATCHED"]',
          style: {
            'border-width': 3,
            'border-color': '#10b981',
          },
        },
        {
          selector: 'node[skillMatchStatus = "MISSING"]',
          style: {
            'border-width': 3,
            'border-color': '#f59e0b',
            'border-style': 'dashed',
          },
        },
        {
          selector: 'node[is_center = "true"]',
          style: {
            width: 68,
            height: 68,
            'font-weight': 'bold',
            'font-size': '12px',
            'border-width': 4,
            'border-color': '#ffffff',
            'border-opacity': 1,
          },
        },
        {
          selector: 'edge',
          style: {
            width: 1.5,
            'line-color': '#334155',
            'target-arrow-color': '#334155',
            'target-arrow-shape': 'triangle',
            'curve-style': 'bezier',
            label: 'data(label)',
            'font-size': '9px',
            color: '#94a3b8',
            'text-rotation': 'autorotate',
            'text-background-opacity': 0.8,
            'text-background-color': '#0f172a',
            'text-background-padding': '2px',
            'text-background-shape': 'roundrectangle',
          },
        },
        {
          selector: 'node:selected',
          style: {
            'border-width': 3,
            'border-color': '#38bdf8',
          },
        },
      ],
      layout: {
        name: 'cose',
        animate: true,
        animationDuration: 800,
        nodeRepulsion: () => 450000,
        idealEdgeLength: () => 100,
        edgeElasticity: () => 100,
        nestingFactor: 5,
        gravity: 80,
        numIter: 1000,
        padding: 50,
      },
    });

    cy.on('tap', 'node', (evt) => {
      const nodeData = evt.target.data();
      setSelectedNode({
        id: nodeData.id,
        type: nodeData.type,
        is_center: nodeData.is_center === 'true',
        label: nodeData.label,
        displayName: nodeData.displayName,
        subtitle: nodeData.subtitle,
        skillMatchStatus: nodeData.skillMatchStatus,
      });
    });

    cy.on('tap', (evt) => {
      if (evt.target === cy) {
        setSelectedNode(null);
      }
    });

    cyRef.current = cy;

    return () => {
      cy.destroy();
    };
  }, [data, showTechnicalLabels]);

  const handleZoomIn = () => {
    if (cyRef.current) {
      cyRef.current.zoom(cyRef.current.zoom() * 1.25);
    }
  };

  const handleZoomOut = () => {
    if (cyRef.current) {
      cyRef.current.zoom(cyRef.current.zoom() * 0.8);
    }
  };

  const handleFit = () => {
    if (cyRef.current) {
      cyRef.current.fit(undefined, 40);
    }
  };

  const handleReset = () => {
    if (cyRef.current) {
      cyRef.current.center();
      cyRef.current.zoom(1);
    }
  };

  return (
    <div style={{ position: 'relative', width: '100%', height, backgroundColor: '#070b14', borderRadius: 'var(--radius-md)', overflow: 'hidden', border: '1px solid var(--border-subtle)' }}>
      {/* Canvas container */}
      <div ref={containerRef} style={{ width: '100%', height: '100%' }} />

      {/* Toolbar Controls */}
      <div
        style={{
          position: 'absolute',
          top: '16px',
          right: '16px',
          display: 'flex',
          flexDirection: 'column',
          gap: '6px',
          backgroundColor: 'rgba(15, 23, 42, 0.85)',
          backdropFilter: 'blur(8px)',
          padding: '6px',
          borderRadius: 'var(--radius-sm)',
          border: '1px solid var(--border-subtle)',
          zIndex: 10,
        }}
      >
        <button
          onClick={handleZoomIn}
          style={{ padding: '6px', color: 'var(--text-primary)', borderRadius: '4px' }}
          title="Zoom In"
        >
          <ZoomIn size={16} />
        </button>
        <button
          onClick={handleZoomOut}
          style={{ padding: '6px', color: 'var(--text-primary)', borderRadius: '4px' }}
          title="Zoom Out"
        >
          <ZoomOut size={16} />
        </button>
        <button
          onClick={handleFit}
          style={{ padding: '6px', color: 'var(--text-primary)', borderRadius: '4px' }}
          title="Fit to Screen"
        >
          <Maximize2 size={16} />
        </button>
        <button
          onClick={handleReset}
          style={{ padding: '6px', color: 'var(--text-primary)', borderRadius: '4px' }}
          title="Reset View"
        >
          <RotateCcw size={16} />
        </button>
      </div>

      {/* Selected Node Details Popup */}
      {selectedNode && (
        <div
          style={{
            position: 'absolute',
            bottom: '16px',
            left: '16px',
            backgroundColor: 'rgba(17, 24, 39, 0.92)',
            backdropFilter: 'blur(8px)',
            border: '1px solid var(--border-focus)',
            borderRadius: 'var(--radius-md)',
            padding: '14px 18px',
            maxWidth: '320px',
            zIndex: 10,
            boxShadow: '0 8px 24px rgba(0, 0, 0, 0.5)',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span
              style={{
                fontSize: '0.75rem',
                textTransform: 'uppercase',
                fontWeight: 600,
                color: ENTITY_COLORS[selectedNode.type]?.text || 'var(--text-secondary)',
              }}
            >
              {selectedNode.type} Node
            </span>
            {selectedNode.is_center && (
              <span style={{ fontSize: '0.7rem', color: 'var(--color-primary)', fontWeight: 600 }}>
                • Focus Center
              </span>
            )}
          </div>
          <div style={{ fontSize: '1.05rem', fontWeight: 700, marginTop: '4px', color: 'var(--text-primary)' }}>
            {selectedNode.displayName || selectedNode.id}
          </div>
          {selectedNode.subtitle && (
            <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '4px', lineHeight: 1.4 }}>
              {selectedNode.subtitle}
            </div>
          )}
          <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', marginTop: '4px' }}>
            ID: {selectedNode.id}
          </div>
          {onNavigateToNode && selectedNode.type !== 'skill' && (
            <button
              onClick={() => onNavigateToNode(selectedNode.type, selectedNode.id)}
              style={{
                marginTop: '10px',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                fontSize: '0.82rem',
                color: 'var(--color-primary)',
                fontWeight: 500,
              }}
            >
              <ExternalLink size={14} />
              Open Node Profile
            </button>
          )}
        </div>
      )}
    </div>
  );
};
