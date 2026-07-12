import { describe, it, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { baseUrlInterceptor } from './base-url.interceptor';
import { GatewayRegistry } from '../config/gateway-registry.service';

describe('baseUrlInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let registry: GatewayRegistry;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([baseUrlInterceptor])),
        provideHttpClientTesting()
      ]
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    registry = TestBed.inject(GatewayRegistry);
    registry.applyConfig({
      activeGatewayId: 'a',
      gateways: [
        { id: 'a', label: 'A', baseUrl: 'http://a.test' },
        { id: 'b', label: 'B', baseUrl: 'http://b.test' }
      ],
      useMock: false,
      healthPollMs: 0,
      showLatency: false
    });
  });

  afterEach(() => httpMock.verify());

  it('prefixes relative URLs with the active gateway base URL', () => {
    http.get('/user-service/users/1').subscribe();
    httpMock.expectOne('http://a.test/user-service/users/1').flush({});
  });

  it('adds a leading slash to path-relative URLs', () => {
    http.get('tweet-service/tweets/1').subscribe();
    httpMock.expectOne('http://a.test/tweet-service/tweets/1').flush({});
  });

  it('reroutes to the newly-active gateway on the next request (the swap)', () => {
    registry.setActive('b');
    http.get('/user-service/users/1').subscribe();
    httpMock.expectOne('http://b.test/user-service/users/1').flush({});
  });

  it('passes absolute URLs through untouched', () => {
    http.get('https://cdn.example.com/x.png').subscribe();
    httpMock.expectOne('https://cdn.example.com/x.png').flush({});
  });

  it('passes assets/* through untouched (config + MSW worker)', () => {
    http.get('assets/config.json').subscribe();
    httpMock.expectOne('assets/config.json').flush({});
    http.get('/assets/icon.svg').subscribe();
    httpMock.expectOne('/assets/icon.svg').flush({});
  });
});
