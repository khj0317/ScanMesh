// API 응답 형식. 서버의 DTO(record)와 필드 이름을 맞춘다

export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
export type Engine = 'CUSTOM' | 'NUCLEI' | 'ZAP';
export type ScanStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED' | 'CANCELLED';
export type TaskStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
export type VerificationMethod = 'DNS_TXT' | 'WELL_KNOWN_FILE';

export const SEVERITIES: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
export const ENGINES: Engine[] = ['CUSTOM', 'NUCLEI', 'ZAP'];

/** 리프레시 토큰은 HttpOnly 쿠키로만 오므로 본문에 없다 */
export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface Member {
  id: number;
  email: string;
  name: string;
  role: 'USER' | 'ADMIN';
  createdAt: string;
}

export interface Target {
  id: number;
  baseUrl: string;
  host: string;
  verified: boolean;
  verifiedAt: string | null;
  verification: {
    method: VerificationMethod;
    token: string;
    dnsRecordName: string;
    fileUrl: string;
  };
  createdAt: string;
}

export interface VerificationResult {
  verified: boolean;
  detail: string;
  target: Target;
}

export interface ScanTask {
  engine: Engine;
  status: TaskStatus;
  progress: number;
  attempt: number;
  maxAttempts: number;
  cancelRequested: boolean;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface Scan {
  id: number;
  targetId: number;
  baseUrl: string;
  status: ScanStatus;
  progress: number;
  requestedAt: string;
  finishedAt: string | null;
  tasks: ScanTask[];
}

export interface FindingLocation {
  fingerprint: string;
  url: string;
  param: string | null;
  severity: Severity;
  engines: Engine[];
  evidence: string | null;
  occurrences: number;
}

export interface Issue {
  check: string;
  title: string;
  severity: Severity;
  cweId: number | null;
  owaspCategory: string | null;
  engines: Engine[];
  remediation: string | null;
  locations: FindingLocation[];
}

export interface ScanFindings {
  scanId: number;
  status: ScanStatus;
  summary: Record<Severity, number>;
  locationCount: number;
  issues: Issue[];
}

export interface WorkerStatus {
  lightOnline: boolean;
  heavyOnline: boolean;
}

export function isFinished(status: ScanStatus): boolean {
  return status === 'COMPLETED' || status === 'PARTIAL' || status === 'FAILED' || status === 'CANCELLED';
}
