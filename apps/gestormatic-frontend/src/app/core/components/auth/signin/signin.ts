import { Component } from '@angular/core';
import { ChangeDetectorRef, inject } from '@angular/core';
import { ViewChild, ElementRef } from '@angular/core';

@Component({
  selector: 'app-signin',
  imports: [],
  templateUrl: './signin.html',
  styleUrl: './signin.css',
})
export class Signin {
  @ViewChild('videoPlayer') video!: ElementRef<HTMLVideoElement>;

  currentSlide = 0;
  intervalId: any;

  textVisible = true;
  isAnimating = true;

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
}
