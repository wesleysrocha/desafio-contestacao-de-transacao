import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, timer } from 'rxjs';
import { takeUntil, switchMap } from 'rxjs/operators';
import { ContestationService } from '../../services/contestation.service';
import { ContestationResponse, ContestationStatus } from '../../models/contestation.model';

@Component({
  selector: 'app-contestation-detail',
  templateUrl: './contestation-detail.component.html'
})
export class ContestationDetailComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  contestation: ContestationResponse | null = null;
  loading = false;
  errorMsg = '';
  successMsg = '';
  autoRefresh = false;
  cancelLoading = false;
  replayLoading = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private contestationService: ContestationService
  ) {}

  ngOnInit(): void {
    const requestId = this.route.snapshot.paramMap.get('id');
    if (requestId) {
      this.loadContestation(requestId);
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadContestation(requestId: string): void {
    this.loading = true;
    this.errorMsg = '';

    this.contestationService.getById(requestId).subscribe({
      next: (data) => {
        this.contestation = data;
        this.loading = false;
      },
      error: (err) => {
        this.errorMsg = err.error?.message || 'Contestação não encontrada';
        this.loading = false;
      }
    });
  }

  refresh(): void {
    if (this.contestation) {
      this.loadContestation(this.contestation.requestId);
    }
  }

  toggleAutoRefresh(): void {
    this.autoRefresh = !this.autoRefresh;
    if (this.autoRefresh && this.contestation) {
      const id = this.contestation.requestId;
      timer(5000, 5000)
        .pipe(
          takeUntil(this.destroy$),
          switchMap(() => this.contestationService.getById(id))
        )
        .subscribe({
          next: (data) => { this.contestation = data; },
          error: () => {}
        });
    } else {
      this.destroy$.next();
    }
  }

  cancelContestation(): void {
    if (!this.contestation) return;
    this.cancelLoading = true;
    this.successMsg = '';
    this.errorMsg = '';

    this.contestationService.cancelContestation(this.contestation.requestId).subscribe({
      next: (data) => {
        this.contestation = data;
        this.successMsg = 'Contestação cancelada com sucesso!';
        this.cancelLoading = false;
      },
      error: (err) => {
        this.errorMsg = err.error?.message || 'Erro ao cancelar contestação';
        this.cancelLoading = false;
      }
    });
  }

  replayContestation(): void {
    if (!this.contestation) return;
    this.replayLoading = true;
    this.successMsg = '';
    this.errorMsg = '';

    this.contestationService.replayContestation(this.contestation.requestId).subscribe({
      next: () => {
        this.successMsg = 'Replay iniciado! Acompanhe as mudanças de status.';
        this.replayLoading = false;
        setTimeout(() => this.refresh(), 2000);
      },
      error: (err) => {
        this.errorMsg = err.error?.message || 'Erro ao iniciar replay';
        this.replayLoading = false;
      }
    });
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

  canCancel(): boolean {
    return this.contestation?.communicationStatus === 'EM_ANDAMENTO';
  }

  canReplay(): boolean {
    const status = this.contestation?.communicationStatus;
    return status === 'CALLBACK_FALHA';
  }

  goBack(): void {
    this.router.navigate(['/contestations']);
  }

  parsedPayload(): string {
    try {
      return JSON.stringify(JSON.parse(this.contestation?.payload || '{}'), null, 2);
    } catch {
      return this.contestation?.payload || '';
    }
  }
}
