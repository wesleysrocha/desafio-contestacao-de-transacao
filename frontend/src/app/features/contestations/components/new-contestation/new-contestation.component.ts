import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ContestationService } from '../../services/contestation.service';
import { CreateContestationResponse } from '../../models/contestation.model';

@Component({
  selector: 'app-new-contestation',
  templateUrl: './new-contestation.component.html'
})
export class NewContestationComponent {
  form: FormGroup;
  loading = false;
  successResponse: CreateContestationResponse | null = null;
  errorMsg = '';

  constructor(
    private fb: FormBuilder,
    private contestationService: ContestationService,
    private router: Router
  ) {
    this.form = this.fb.group({
      contestationId: ['', [Validators.required, Validators.minLength(3)]],
      description: [''],
      amount: [''],
      channel: ['']
    });
  }

  get f() { return this.form.controls; }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.errorMsg = '';
    this.successResponse = null;

    const { contestationId, description, amount, channel } = this.form.value;

    const payload = {
      contestationId,
      ...(description && { description }),
      ...(amount && { amount: Number(amount) }),
      ...(channel && { channel })
    };

    this.contestationService.createContestation(payload).subscribe({
      next: (response) => {
        this.successResponse = response;
        this.loading = false;
      },
      error: (err) => {
        this.errorMsg = err.error?.message || 'Erro ao criar contestação. Verifique os dados.';
        this.loading = false;
      }
    });
  }

  viewDetail(requestId: string): void {
    this.router.navigate(['/contestations', requestId]);
  }

  newContestation(): void {
    this.form.reset();
    this.successResponse = null;
    this.errorMsg = '';
  }

  getStatusClass(status: string): string {
    const map: Record<string, string> = {
      'EM_ANDAMENTO': 'badge-em-andamento',
      'SUCESSO': 'badge-sucesso',
      'CANCELADO': 'badge-cancelado',
      'CALLBACK_FALHA': 'badge-callback-falha'
    };
    return map[status] || '';
  }
}
