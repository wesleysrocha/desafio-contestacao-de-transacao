import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  template: `
    <nav class="navbar">
      <a class="navbar-brand" routerLink="/contestations">
        <span class="logo-icon">
        <img src="/assets/itau-logo.png" alt="Logo Prevenção de Fraudes" class="logo-img">
        </span>
        <span class="logo-text">Prevenção a Fraudes</span>
      </a>
      <ul class="navbar-nav">
        <li>
          <a routerLink="/contestations" routerLinkActive="active" [routerLinkActiveOptions]="{exact: true}">
            📊 Contestações
          </a>
        </li>
        <li>
          <a routerLink="/contestations/new" routerLinkActive="active">
            ➕ Nova Contestação
          </a>
        </li>
      </ul>
    </nav>
    <router-outlet></router-outlet>
  `
})
export class AppComponent {
  title = 'prevencao-fraudes-frontend';
}
