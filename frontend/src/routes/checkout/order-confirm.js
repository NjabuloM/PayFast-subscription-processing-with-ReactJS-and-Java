import React, { useEffect, useRef, useState } from "react";
import { useSelector, useDispatch } from "react-redux";
import { viewMagazineDetails, payForSubscription } from "../../redux/subscription/reducer";
import { Row, Col, Card, CardTitle, CardText, Form, FormGroup, Input, Label, Button } from "reactstrap";

const PLAN_LABELS = {
    MONTHLY: 'Monthly',
    ANNUALLY: 'Annually'
};

const OrderConfirmation = () => {
    const dispatch = useDispatch();
    const magazine = useSelector(state => state.subscribe.magazine);
    const plans = useSelector(state => state.subscribe.plans);
    const checkoutForm = useSelector(state => state.subscribe.formData);
    const payFastForm = useRef(null);

    useEffect(() => {
        dispatch(viewMagazineDetails());
        // eslint-disable-next-line
    }, []);

    // Once the backend has returned the signed form, post it to PayFast. The backend decides where
    // (sandbox or live), so the browser never needs to know.
    const [paymentFrequency, setPaymentFrequency] = useState('');
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [email, setEmail] = useState('');
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        if (checkoutForm.action && Array.isArray(checkoutForm.fields) && payFastForm.current) {
            payFastForm.current.submit();
        } else if (checkoutForm.error) {
            setSubmitting(false);
        }
    }, [checkoutForm]);

    const handleCheckOut = () => {
        setSubmitting(true);
        dispatch(payForSubscription({
            'firstName': firstName,
            'lastName': lastName,
            'emailAddress': email,
            // Only the choice is sent; the backend works out the price
            'paymentFrequency': paymentFrequency,
            'magazineId': magazine.id
        }));
    };

    return(
        <Row>
            <Col sm="6">
                <Card body>
                    <CardTitle tag="h5">
                        Confirm Order
                    </CardTitle>
                    <CardText>
                        {magazine.name}
                    </CardText>

                    <FormGroup row tag="fieldset">
                        <legend className="col-form-label col-sm-2">
                            Subscription type
                        </legend>
                        <Col sm={10}>
                            {plans.map(plan => (
                                <FormGroup check key={plan.frequency}>
                                    <Input
                                        name="radioOption"
                                        type="radio"
                                        id={plan.frequency}
                                        checked={paymentFrequency === plan.frequency}
                                        onChange={() => setPaymentFrequency(plan.frequency)}
                                    />
                                    {' '}
                                    <Label check for={plan.frequency}>
                                        {PLAN_LABELS[plan.frequency] || plan.frequency} at R {Number(plan.amount).toFixed(2)}
                                    </Label>
                                </FormGroup>
                            ))}
                        </Col>
                    </FormGroup>
                </Card>
            </Col>
            <Col sm="6">
                <Card body>
                    <CardTitle tag="h5">
                        Contact Info
                    </CardTitle>
                    <Form>
                        <Row>
                            <Col md={6}>
                                <FormGroup>
                                    <Label for="firstName">
                                        First name
                                    </Label>
                                    <Input
                                        id="firstName"
                                        type="text"
                                        onChange={(event) => setFirstName(event.target.value)}
                                    />
                                </FormGroup>
                            </Col>
                            <Col md={6}>
                                <FormGroup>
                                    <Label for="lastName">
                                        Last name
                                    </Label>
                                    <Input
                                        id="lastName"
                                        type="text"
                                        onChange={(event) => setLastName(event.target.value)}
                                    />
                                </FormGroup>
                            </Col>
                        </Row>
                        <Row>
                            <FormGroup>
                                <Label for="emailAddress">
                                    Email
                                </Label>
                                <Input
                                    id="emailAddress"
                                    name="email"
                                    placeholder="someone@example.co"
                                    type="email"
                                    onChange={(event) => setEmail(event.target.value)}
                                />
                            </FormGroup>
                        </Row>
                    </Form>
                    <Button onClick={handleCheckOut} disabled={!paymentFrequency || !email || submitting}>
                        Check Out
                    </Button>
                    {checkoutForm.error && !submitting && (
                        <CardText className="text-danger">{checkoutForm.error}</CardText>
                    )}
                </Card>
            </Col>

            {/* The signed form returned by the backend; submitted automatically once it arrives */}
            {checkoutForm.action && Array.isArray(checkoutForm.fields) && (
                <form ref={payFastForm} method="POST" action={checkoutForm.action} hidden>
                    {checkoutForm.fields.map(field => (
                        <input key={field.name} type={field.type} name={field.name} value={field.value} readOnly />
                    ))}
                </form>
            )}
        </Row>
    );
}

export default OrderConfirmation;
