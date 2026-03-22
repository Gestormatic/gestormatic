import { Component } from '@angular/core';
import { ChangeDetectorRef, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Auth } from '../../../services/auth';

@Component({
  selector: 'app-signin',
  imports: [ReactiveFormsModule],
  templateUrl: './signin.html',
  styleUrl: './signin.css',
})
export class Signin {
  private fb = inject(FormBuilder);
  private authService = inject(Auth);
  private route = inject(Router);

  currentSlide = 0;
  intervalId: any;
  loading = false;
  errorMessage = '';

  signinForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  state: 'idle' | 'loading' | 'success' = 'idle';

  slides = [
    {
      image: '/images/3d-casual-life-picking-user-window.webp',
      title: 'Tus trámites, bajo control.',
      description: 'Centraliza expedientes y reduce tiempos de respuesta.',
    },
    {
      image: '/images/browser-window-with-combined-chart-and-ai-assistant.webp',
      title: 'Decisiones con datos.',
      description: 'Visualiza métricas en tiempo real.',
    },
    {
      image: '/images/3d-isometric-pre-launch-planning-for-a-startup-1.webp',
      title: 'Automatiza tu flujo.',
      description: 'Menos tareas manuales, más eficiencia.',
    },
  ];

  private cdr = inject(ChangeDetectorRef);

  ngOnInit() {
    this.startAutoSlide();
  }

  startAutoSlide() {
    this.intervalId = setInterval(() => {
      this.nextSlide();
      this.cdr.detectChanges(); // 🔥 clave
    }, 6000);
  }

  nextSlide() {
    this.currentSlide = (this.currentSlide + 1) % this.slides.length;
  }

  stopAutoSlide() {
    clearInterval(this.intervalId);
  }

  goToSlide(index: number) {
    this.currentSlide = index;
  }

  onSubmit() {
    if (this.signinForm.invalid) {
      this.signinForm.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    const { email, password } = this.signinForm.value;

    if (this.signinForm.invalid) {
      this.signinForm.markAllAsTouched();
      return;
    }

    this.state = 'loading';

    this.authService
      .signIn(email!, password!)
      .then(() => {
        this.state = 'success';
        setTimeout(() => {
          console.log('Login correcto');
          this.route.navigate(['main/home']);
        }, 800);
      })
      .catch((error) => {
        this.state = 'idle';
        this.errorMessage = error.message || 'Error desconocido';
      });
  }
}
