import React from "react";
import { useSelector } from "react-redux";
import Magazine from "../Magazine";
import { Row, Col } from "reactstrap";

const DisplayCabinet = () => {
    const {newsstand, loading} = useSelector(newsstand => newsstand.catalogue)

    return(
        <div>
            {loading ? (
                <p>Loading...</p>
            ) : (
                newsstand.map((magazine, index) => {
                    return(
                        <Row key={index}>
                            <Col>
                                <Magazine magazine={magazine} />
                            </Col>
                        </Row>
                    )                    
                })
            )}
        </div>
    );
}

export default DisplayCabinet;