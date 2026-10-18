import {
  buildProxyCheapLocations,
  buildProxyCheapOrderPayload,
  parseProxyCheapLocation,
} from './proxyCheapLocations';

describe('Proxy-Cheap provider location mapping', () => {
  const setup = {
    countries: [{ code: 'US', name: 'United States' }],
    regions: {
      US: [{ id: 'Virginia', code: 'VA', label: 'Virginia', isps: [{ id: 'isp-17', label: 'Provider 17' }] }],
    },
    cities: {
      US: [{ id: 'ashburn-va', name: 'Ashburn', region: 'Virginia', isps: [{ id: 'isp-17', label: 'Provider 17' }] }],
    },
    isps: {
      US: [{ id: 'isp-17', label: 'Provider 17' }],
    },
  };

  it('keeps the human-readable region label separate from the provider region code', () => {
    const locations = buildProxyCheapLocations(setup);
    const ashburn = locations.find((location) => location.state === 'Virginia' && location.isp.startsWith('Ashburn •'));

    expect(ashburn).toBeDefined();
    expect(parseProxyCheapLocation(ashburn?.id)).toEqual({
      country: 'US',
      region: 'VA',
      city: 'ashburn-va',
      ispId: 'isp-17',
    });
  });

  it('resolves a city region label to the provider code and shares the same payload for quote and checkout', () => {
    const location = buildProxyCheapLocations(setup).find((candidate) => candidate.state === 'Virginia' && candidate.isp.startsWith('Ashburn •'));
    const quotePayload = buildProxyCheapOrderPayload({ locationId: location?.id, planId: 'basic', periodMonths: 1 });
    const checkoutPayload = buildProxyCheapOrderPayload({ locationId: location?.id, planId: 'basic', periodMonths: 1 });

    expect(quotePayload).toEqual(checkoutPayload);
    expect(quotePayload).toMatchObject({
      country: 'US',
      region: 'VA',
      city: 'ashburn-va',
      ispId: 'isp-17',
    });
  });

  it('continues to parse legacy pipe-delimited location IDs', () => {
    expect(parseProxyCheapLocation('US|VA|Ashburn|isp-17')).toEqual({
      country: 'US',
      region: 'VA',
      city: 'Ashburn',
      ispId: 'isp-17',
    });
  });
});
