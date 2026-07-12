import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { latencyInterceptor } from './latency.interceptor';
import { LatencyService } from '../state/latency.service';

describe('latencyInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let latency: LatencyService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([latencyInterceptor])),
        provideHttpClientTesting()
      ]
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    latency = TestBed.inject(LatencyService);
  });

  afterEach(() => httpMock.verify());

  it('records a round-trip time on a successful response', () => {
    expect(latency.lastMs()).toBeNull();
    http.get('/x').subscribe();
    httpMock.expectOne('/x').flush({});
    expect(latency.lastMs()).not.toBeNull();
    expect(latency.lastMs()).toBeGreaterThanOrEqual(0);
  });
});
