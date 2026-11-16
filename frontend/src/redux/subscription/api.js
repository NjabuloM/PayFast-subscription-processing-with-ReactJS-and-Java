import { getHostURL } from "../catalogue/api";

export const addSubscription = (selectedMagazineId) => {
    //Short-circuit the trip to the DB, and save the selection locally (for Demo purposes)
    localStorage.setItem('selected_magazine_id', selectedMagazineId);
}

// Returns { action, fields }: the PayFast URL to post to (sandbox or live, decided by the backend) and the
// signed hidden form fields.
export const checkOut = (subscriptionInfo) => {
    const CHECKOUT_ENDPOINT = getHostURL() + '/shop/newsstand/checkout';
    return fetch(CHECKOUT_ENDPOINT, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            firstName: subscriptionInfo.firstName, lastName: subscriptionInfo.lastName,
            emailAddress: subscriptionInfo.emailAddress, magazineId: subscriptionInfo.magazineId,
            paymentFrequency: subscriptionInfo.paymentFrequency
        })
    })
    .then(response => {
		let json = response.json();
		if (response.status >= 200 && response.status < 300) {
			return json;
		} else {
			return json.then(Promise.reject.bind(Promise));
		}
	});
}
