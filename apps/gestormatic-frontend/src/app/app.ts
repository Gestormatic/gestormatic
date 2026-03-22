import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { Layout } from './core/components/layout/layout';
import { Signin } from './core/components/auth/signin/signin';

@Component({
  imports: [RouterModule, Signin, Layout],
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected title = 'Gestormatic';
}
