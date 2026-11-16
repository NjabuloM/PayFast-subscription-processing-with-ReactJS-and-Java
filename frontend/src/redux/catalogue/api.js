const handleJsonResponse = response => {
    let jsonResponse = response.json();
    if (response.status === 200) {
        return jsonResponse;
    } else {
        return jsonResponse.then(Promise.reject.bind(Promise));
    }
};

export const getMagazineCatalogue = () => {
    const CATALOGUE_ENDPOINT = getHostURL() + '/shop/newsstand';
    return fetch(CATALOGUE_ENDPOINT, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(handleJsonResponse);
}

export const getMagazine = (magazineId) => {
    const MAGAZINE_ENDPOINT = getHostURL() + '/shop/newsstand/magazine/' + magazineId;
    return fetch(MAGAZINE_ENDPOINT, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(handleJsonResponse);
}

// The billing options for a magazine, e.g. [{ frequency: 'MONTHLY', amount: 44.90, cycles: 12 }, ...].
// Prices are worked out by the backend, never in the browser.
export const getPlans = (magazineId) => {
    const PLANS_ENDPOINT = getHostURL() + '/shop/newsstand/magazine/' + magazineId + '/plans';
    return fetch(PLANS_ENDPOINT, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(handleJsonResponse);
}

export function getHostURL() {
    const env_api_host = process.env.REACT_APP_SPAZA_SHOP_API_URI;
    return env_api_host ? env_api_host : 'http://localhost:8080';
}
