import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { CommonModule, DatePipe } from '@angular/common';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { NewContestationComponent } from './features/contestations/components/new-contestation/new-contestation.component';
import { ContestationListComponent } from './features/contestations/components/contestation-list/contestation-list.component';
import { ContestationDetailComponent } from './features/contestations/components/contestation-detail/contestation-detail.component';

@NgModule({
  declarations: [
    AppComponent,
    NewContestationComponent,
    ContestationListComponent,
    ContestationDetailComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    ReactiveFormsModule,
    FormsModule,
    CommonModule,
    AppRoutingModule
  ],
  providers: [DatePipe],
  bootstrap: [AppComponent]
})
export class AppModule {}
