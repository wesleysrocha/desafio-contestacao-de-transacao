import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { NewContestationComponent } from './features/contestations/components/new-contestation/new-contestation.component';
import { ContestationListComponent } from './features/contestations/components/contestation-list/contestation-list.component';
import { ContestationDetailComponent } from './features/contestations/components/contestation-detail/contestation-detail.component';

const routes: Routes = [
  { path: '', redirectTo: '/contestations', pathMatch: 'full' },
  { path: 'dashboard', redirectTo: '/contestations', pathMatch: 'full' },
  { path: 'contestations/new', component: NewContestationComponent },
  { path: 'contestations/:id', component: ContestationDetailComponent },
  { path: 'contestations', component: ContestationListComponent },
  { path: '**', redirectTo: '/contestations' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}
