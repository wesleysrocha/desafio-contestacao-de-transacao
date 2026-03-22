import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  ContestationFilters,
  ContestationResponse,
  CreateContestationRequest,
  CreateContestationResponse,
  PageResponse
} from '../models/contestation.model';

@Injectable({
  providedIn: 'root'
})
export class ContestationService {
  private readonly baseUrl = `${environment.apiBaseUrl}/contestations`;

  constructor(private http: HttpClient) {}

  createContestation(request: CreateContestationRequest): Observable<CreateContestationResponse> {
    return this.http.post<CreateContestationResponse>(this.baseUrl, request);
  }

  getById(requestId: string): Observable<ContestationResponse> {
    return this.http.get<ContestationResponse>(`${this.baseUrl}/${requestId}`);
  }

  listContestations(filters: ContestationFilters): Observable<PageResponse<ContestationResponse>> {
    let params = new HttpParams()
      .set('page', filters.page.toString())
      .set('size', filters.size.toString());

    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.contestationId) {
      params = params.set('contestationId', filters.contestationId);
    }
    if (filters.fromDate) {
      params = params.set('fromDate', filters.fromDate);
    }
    if (filters.toDate) {
      params = params.set('toDate', filters.toDate);
    }

    return this.http.get<PageResponse<ContestationResponse>>(this.baseUrl, { params });
  }

  cancelContestation(requestId: string): Observable<ContestationResponse> {
    return this.http.post<ContestationResponse>(`${this.baseUrl}/${requestId}/cancel`, {});
  }

  replayContestation(requestId: string): Observable<{ message: string; requestId: string }> {
    return this.http.post<{ message: string; requestId: string }>(
      `${environment.apiBaseUrl}/admin/replay/${requestId}`, {}
    );
  }
}
