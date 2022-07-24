export const addSubscription = (selectedMagazineId) => {
    //Short-circuit the trip to the DB, and save the selection locally (for Demo purposes)
    localStorage.setItem('selected_magazine_id', selectedMagazineId);
}

export const checkOut = (subscriptionInfo) => {
    var host = 'http://localhost:8080';

    const CHECKOUT_ENDPOINT = host + '/shop/newsstand/checkout';
    return fetch(CHECKOUT_ENDPOINT, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            firstName: subscriptionInfo.firstName, lastName: subscriptionInfo.lastName,
            emailAddress: subscriptionInfo.emailAddress, magazineId: subscriptionInfo.magazineId
        })
    })
    .then(response => {
		let json = response.json();
		if (response.status >= 200 && response.status < 300) {
			return json;
		} else {
			return json.then(Promise.reject.bind(Promise));
		}		 
	})
    .then(formData => {
        return formData;
    });
}
