import { Component, ChangeDetectorRef, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Auth } from '../../../services/auth';

@Component({
  selector: 'app-signup',
  imports: [ReactiveFormsModule],
  templateUrl: './signup.html',
  styleUrl: './signup.css',
})
export class Signup implements OnInit {
  private fb = inject(FormBuilder);
  private authService = inject(Auth);
  private route = inject(Router);

  currentSlide = 0;
  intervalId: any;
  loading = false;
  errorMessage = '';
  showPassword = false;

  signupForm = this.fb.group({
    displayName: ['', [Validators.required]],
    tenantId: ['', [Validators.required]],
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

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  onSubmit() {
    if (this.signupForm.invalid) {
      this.signupForm.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    const { email, password, displayName } = this.signupForm.value;

    if (this.signupForm.invalid) {
      this.signupForm.markAllAsTouched();
      return;
    }

    this.state = 'loading';

    this.authService
      .signUp(email!, password!, displayName!)
      .then(() => {
        this.state = 'success';
        setTimeout(() => {
          console.log('Registro correcto');
          this.route.navigate(['auth/signin']);
        }, 800);
      })
      .catch((error) => {
        this.state = 'idle';
        this.errorMessage = error.message || 'Error desconocido';
      });
  }
}
