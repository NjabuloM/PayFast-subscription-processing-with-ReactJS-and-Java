import React, { useEffect, useState } from "react";
import { useSelector, useDispatch } from "react-redux";
import { viewMagazineDetails, payForSubscription } from "../../redux/subscription/reducer";
import { Row, Col, Card, CardTitle, CardText, Form, FormGroup, Input, Label, Button } from "reactstrap";

const gateway_path = "https://sandbox.payfast.co.za/eng/process";

const OrderConfirmation = () => {
    const dispatch = useDispatch(); 
    const magazine = useSelector(state => {
        return state.subscribe.magazine;
    });

    useEffect(() => {
        dispatch(viewMagazineDetails());
        // eslint-disable-next-line
    }, []);

    const receiveFormData = useSelector(state => state.subscribe.formData);
    useEffect(() => {
        if (Array.isArray(receiveFormData)) {
            //Build hidden form for submition
            var form = document.createElement("form");
            form.setAttribute("method", "POST");
            form.setAttribute("action", gateway_path);

            for(var element in receiveFormData) {
                if(receiveFormData.hasOwnProperty(element)) {
                    var hiddenField = document.createElement("input");
                    hiddenField.setAttribute("type", receiveFormData[element].type);
                    hiddenField.setAttribute("name", receiveFormData[element].name);
                    hiddenField.setAttribute("value", receiveFormData[element].value);

                    form.appendChild(hiddenField);				
                }
            }        

            form._submit_function_ = form.submit;

            document.body.appendChild(form);
            form._submit_function_();
        } 
    }, [receiveFormData]);

    const [subscriptionType, setSubscriptionType] = useState();
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [email, setEmail] = useState('');

    function handleSubscriptionTypeSelection(event) {
        const optionSelected = event.target.id;
        if (optionSelected === 'annualRadio') {
            const annualSubscription = {
                'name': 'yearly',
                'value': Math.ceil(magazine.price * 12 - magazine.price * 12 * 0.1)
            };
            setSubscriptionType(annualSubscription);
        } else if (optionSelected === 'monthRadio') {
            const monthlySubscription = {
                'name': 'monthly',
                'value': magazine.price
            };
            setSubscriptionType(monthlySubscription);
        }
    }

    const handleCheckOut = () => {
        const subDetails = {
            'firstName': firstName,
            'lastName': lastName,
            'emailAddress': email,
            'paymentFrequency': subscriptionType ,
            'magazineId': magazine.id
        }
        dispatch(payForSubscription(subDetails));
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
                    
                    <FormGroup
                        row
                        tag="fieldset"
                        onChange={(event) => handleSubscriptionTypeSelection(event)}
                        >
                        <legend className="col-form-label col-sm-2">
                            Subscription type
                        </legend>
                        <Col sm={10}>
                            <FormGroup check>
                                <Input
                                    name="radioOption"
                                    type="radio"
                                    id="annualRadio"
                                />
                                {' '}
                                <Label check>
                                    Annually at R {Math.ceil(magazine.price * 12 - magazine.price * 12 * 0.1)}
                                </Label>
                            </FormGroup>
                            <FormGroup check>
                                <Input
                                    name="radioOption"
                                    type="radio"
                                    id="monthRadio"
                                />
                                {' '}
                                <Label check>
                                    Monthly at R {magazine.price}
                                </Label>
                            </FormGroup>   
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
                    <Button onClick={handleCheckOut}>
                        Check Out
                    </Button>
                </Card>
            </Col>
        </Row>        
    );
}

export default OrderConfirmation;