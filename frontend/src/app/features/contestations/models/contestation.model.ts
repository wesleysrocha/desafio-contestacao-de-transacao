export type ContestationType = 'CONTESTACAO_ABERTA';

export type ContestationStatus =
  | 'EM_ANDAMENTO'
  | 'CANCELADO'
  | 'SUCESSO'
  | 'CALLBACK_FALHA';

export interface AuditLogEntry {
  id: string;
  fromStatus: ContestationStatus | null;
  toStatus: ContestationStatus;
  message: string;
  createdAt: string;
}

export interface ContestationResponse {
  requestId: string;
  contestationId: string;
  communicationType: ContestationType;
  communicationStatus: ContestationStatus;
  payload: string;
  lastError: string | null;
  correlationId: string;
  receivedAt: string;
  updatedAt: string;
  auditHistory: AuditLogEntry[];
}

export interface CreateContestationRequest {
  contestationId: string;
  [key: string]: unknown;
}

export interface CreateContestationResponse {
  requestId: string;
  status: ContestationStatus;
  receivedAt: string;
  correlationId: string;
  idempotent: boolean;
}

export interface PageResponse<T> {
  content: T[];
  pagination: {
    pageNumber: number;
    pageSize: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface ContestationFilters {
  page: number;
  size: number;
  status?: ContestationStatus | '';
  contestationId?: string;
  fromDate?: string;
  toDate?: string;
}
