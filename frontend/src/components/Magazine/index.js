import React from "react";
import { Card, CardTitle, CardText, Button } from "reactstrap";
import { useDispatch } from "react-redux";
import { selectSubscription } from "../../redux/subscription/reducer";
import { useNavigate } from "react-router-dom";

const Magazine = ({magazine: {id, name, month, year, cover}}) => {
    const dispatch = useDispatch(); 
    const navigate = useNavigate();

    const handleClick = (event) => {
        const magazineId = event.target.id;
        dispatch(selectSubscription({"selectedMagazineId": magazineId}));
        navigate("/confirm-order", { replace: false });
    };

    return(
        <Card body key={id} className="magazineTile">
            <CardTitle><b>{name}</b></CardTitle>
            <CardText>{month} issue of {year}</CardText>
            <Button id={id} className="subscribeNow" onClick={handleClick}>Subscribe Now</Button>
        </Card>
    );
}

export default Magazine;