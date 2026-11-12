import { PredictionResponse } from './prediction';

export interface RecommendationItem {
  priority: 'HIGH' | 'MEDIUM' | 'LOW' | string;
  category: string;
  title: string;
  action: string;
  currentValue: string;
  targetValue: string;
}

export interface PeerBenchmark {
  metric: string;
  studentValue: number;
  selectedPeerAvg: number;
  status: 'ON_TRACK' | 'BELOW_PEER' | string;
}

export interface ApplicationRecommendationReport {
  readinessLevel: string;
  summaryHeadline: string;
  jobTitle: string;
  jobDomain: string;
  skillMatchPercent: number;
  matchedSkills: string[];
  missingSkills: string[];
  items: RecommendationItem[];
  peerBenchmarks: PeerBenchmark[];
}

export interface Application {
  applicationId: string;
  studentId: string;
  companyId: string;
  jobId: string;
  cycle: number;
  relevantExperienceMonths: number;
  totalSkillCount: number;
  averageSkillProficiency: number;
  roleShiftScore: number;
  skillMatchRatio: number;
  requiredSkillLevelGap: number;
  roleExperienceMatch: number;
  expectedHiringCount: number;
  finalStatus: number;
  finalStatusLabel: string;
  latestPrediction?: PredictionResponse | null;
  recommendations?: ApplicationRecommendationReport | null;
}

export interface ApplicationFilters {
  cycle?: number;
  finalStatus?: number;
}
