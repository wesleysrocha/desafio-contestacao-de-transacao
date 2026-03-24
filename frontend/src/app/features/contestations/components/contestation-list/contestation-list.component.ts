import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, Subscription, timer } from 'rxjs';
import { takeUntil, switchMap } from 'rxjs/operators';
import { ContestationService } from '../../services/contestation.service';
import {
  ContestationFilters,
  ContestationResponse,
  ContestationStatus,
  PageResponse
} from '../../models/contestation.model';

@Component({
  selector: 'app-contestation-list',
  templateUrl: './contestation-list.component.html'
})
export class ContestationListComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private refreshDestroy$ = new Subject<void>();

  pageData: PageResponse<ContestationResponse> | null = null;
  loading = false;
  errorMsg = '';

  filters: ContestationFilters = {
    page: 0,
    size: 10,
    status: '',
    contestationId: '',
    fromDate: '',
    toDate: ''
  };

  statusOptions: { label: string; value: ContestationStatus | '' }[] = [
    { label: 'Todos', value: '' },
    { label: 'Em Andamento', value: 'EM_ANDAMENTO' },
    { label: 'Sucesso', value: 'SUCESSO' },
    { label: 'Cancelado', value: 'CANCELADO' },
    { label: 'Callback Falha', value: 'CALLBACK_FALHA' }
  ];

  autoRefresh = false;

  constructor(
    private contestationService: ContestationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadContestations();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.refreshDestroy$.next();
    this.refreshDestroy$.complete();
  }

  loadContestations(): void {
    this.loading = true;
    this.errorMsg = '';

    this.contestationService.listContestations(this.filters).subscribe({
      next: (data) => {
        this.pageData = data;
        this.loading = false;
      },
      error: (err) => {
        this.errorMsg = err.error?.message || 'Erro ao carregar contestações';
        this.loading = false;
      }
    });
  }

  applyFilters(): void {
    this.filters.page = 0;
    this.loadContestations();
  }

  clearFilters(): void {
    this.filters = {
      page: 0,
      size: 10,
      status: '',
      contestationId: '',
      fromDate: '',
      toDate: ''
    };
    this.loadContestations();
  }

  goToPage(page: number): void {
    this.filters.page = page;
    this.loadContestations();
  }

  viewDetail(requestId: string): void {
    this.router.navigate(['/contestations', requestId]);
  }

  getStatusClass(status: ContestationStatus): string {
    const map: Record<ContestationStatus, string> = {
      'EM_ANDAMENTO': 'badge-em-andamento',
      'SUCESSO': 'badge-sucesso',
      'CANCELADO': 'badge-cancelado',
      'CALLBACK_FALHA': 'badge-callback-falha'
    };
    return map[status] || '';
  }

  getStatusIcon(status: ContestationStatus): string {
    const icons: Record<ContestationStatus, string> = {
      'EM_ANDAMENTO': '⏳',
      'SUCESSO': '✅',
      'CANCELADO': '🚫',
      'CALLBACK_FALHA': '❌'
    };
    return icons[status] || '•';
  }

  get pages(): number[] {
    if (!this.pageData) return [];
    return Array.from({ length: this.pageData.pagination.totalPages }, (_, i) => i);
  }

  toggleAutoRefresh(): void {
    this.autoRefresh = !this.autoRefresh;
    if (this.autoRefresh) {
      this.refreshDestroy$ = new Subject<void>();
      timer(5000, 5000)
        .pipe(
          takeUntil(this.refreshDestroy$),
          switchMap(() => this.contestationService.listContestations(this.filters))
        )
        .subscribe({
          next: (data) => { this.pageData = data; },
          error: () => {}
        });
    } else {
      this.refreshDestroy$.next();
    }
  }
}
