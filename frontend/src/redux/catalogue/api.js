export const getMagazineCatalogue = () => {
    var host = getHostURL();

    const CATALOGUE_ENDPOINT = host + '/shop/newsstand';
    return fetch(CATALOGUE_ENDPOINT, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(response => {
        let jsonResponse = response.json();
        if (response.status === 200) {
            return jsonResponse;
        } else {
            return jsonResponse.then(Promise.reject.bind(Promise));
        }
    })
    .then(newsstand => {
        return newsstand;
    });
}

export const getMagazine = (magazineId) => {
    var host = getHostURL();

    const MAGAZINE_ENDPOINT = host + '/shop/newsstand/magazine/' + magazineId;
    return fetch(MAGAZINE_ENDPOINT, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(response => {
        let jsonResponse = response.json();
        if (response.status === 200) {
            return jsonResponse;
        } else {
            return jsonResponse.then(Promise.reject.bind(Promise));
        }
    })
    .then(magazine => {
        return magazine;
    });
}

function getHostURL() {
    const env_api_host = process.env.REACT_APP_SPAZA_SHOP_API_URI;

	var host = '';
	if ( (null !== env_api_host) && (env_api_host !== undefined))  {
		host = env_api_host;
	} else {
		host = 'http://localhost:8181';
	}

    return host;
}